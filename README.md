# BedrockBridge

Extensão de compatibilidade entre as cinematics do Typewriter e jogadores Bedrock ligados pelo Geyser. Funciona com o Geyser instalado no mesmo Paper ou, em redes com proxy, com o Geyser instalado apenas no Velocity. O comportamento dos jogadores Java não é alterado.

## Funcionalidades

- Deteta jogadores Bedrock pela API local do Geyser ou pela API do Floodgate no backend.
- Oculta o HUD Bedrock durante uma cinematic e repõe exatamente o estado anterior no fim.
- Mantém o HUD oculto caso outra parte do Typewriter o reponha a meio da cinematic.
- Repõe o estado ao terminar ou trocar de cinematic, ao mudar de servidor, ao sair e durante unload/reload.
- Encaminha sons personalizados diretamente para o cliente Bedrock quando o som existe num pack carregado pelo Geyser.
- Sincroniza com o Paper o catálogo de sons que está instalado no Geyser-Velocity.
- Falha de forma segura: se a ponte, o pack ou o som não estiverem disponíveis, o pacote Java original não é cancelado.
- Disponibiliza diagnóstico através de comandos e da consola.

## Requisitos

Requisitos comuns:

- Java 21 no Paper e no Velocity.
- Paper 1.21.10 com Typewriter `0.9.0-beta-167` e a BasicExtension da mesma build.
- PacketEvents 2.9.4, a versão usada pelo Typewriter 0.9.0.

Escolhe um dos modos seguintes:

### Geyser no Paper

- Geyser-Spigot instalado no servidor Paper.
- O JAR principal do BedrockBridge instalado como extensão do Typewriter.

### Geyser no Velocity

- Geyser-Velocity e Floodgate-Velocity instalados no proxy.
- Floodgate-Spigot instalado no servidor Paper para disponibilizar a API de deteção Bedrock.
- O JAR principal do BedrockBridge instalado como extensão do Typewriter.
- O JAR BedrockBridge-Velocity instalado como plugin do proxy.

Neste segundo modo não é preciso instalar o Geyser no Paper. Esta build está ligada exatamente ao Typewriter `0.9.0-beta-167`; volta a compilar a extensão sempre que mudares a build do Typewriter.

## Compilação

Executa:

```shell
./gradlew clean build --no-daemon
```

No Windows:

```powershell
.\gradlew.bat clean build --no-daemon
```

A build cria dois JARs para instalar:

- `build/libs/BedrockBridge-0.1.0-SNAPSHOT.jar`: extensão do Typewriter para o Paper;
- `velocity/build/libs/BedrockBridge-Velocity-0.1.0-SNAPSHOT.jar`: plugin para o Velocity.

O JAR criado em `protocol/build/libs` é apenas um módulo interno e não deve ser instalado.

## Instalação com Geyser no Velocity

1. Copia `BedrockBridge-0.1.0-SNAPSHOT.jar` para `plugins/Typewriter/extensions` no Paper.
2. Copia `BedrockBridge-Velocity-0.1.0-SNAPSHOT.jar` para `plugins` no Velocity.
3. Confirma que Geyser-Velocity e Floodgate-Velocity estão ativos no proxy e que Floodgate-Spigot está ativo no Paper.
4. No Floodgate do proxy, define `send-floodgate-data: true`.
5. Copia a `key.pem` do Floodgate do proxy para a pasta do Floodgate no Paper. A chave tem de ser exatamente a mesma nos dois lados.
6. No Geyser-Velocity, usa `auth-type: floodgate` e coloca o pack Bedrock em `plugins/Geyser-Velocity/packs`.
7. Configura o encaminhamento de jogadores do Velocity e protege o backend para aceitar ligações apenas do proxy.
8. Reinicia primeiro o proxy e depois o Paper. Os jogadores Bedrock devem voltar a ligar para receber o pack.

Os JARs de extensões do Typewriter não devem ser colocados diretamente na pasta `plugins` do Paper. A comunicação entre os dois JARs usa o canal `lusiadascraft:bedrockbridge`; não é preciso ativar o canal de compatibilidade BungeeCord no Velocity.

## Instalação com Geyser no Paper

1. Copia `BedrockBridge-0.1.0-SNAPSHOT.jar` para `plugins/Typewriter/extensions`.
2. Instala o Geyser-Spigot no mesmo servidor.
3. Coloca o pack Bedrock em `plugins/Geyser-Spigot/packs`.
4. Reinicia o servidor ou executa `/typewriter reload`.

Neste modo não instales o JAR BedrockBridge-Velocity.

## Diagnóstico

Os comandos requerem a permissão `typewriter.bedrockbridge.debug`.

- `/tw bedrockbridge` mostra o estado da ligação Bedrock, cinematics ativas, sons encontrados, estado do transporte e quantidade de sons encaminhados.
- `/tw bedrockbridge check [jogador]` indica se o jogador é Java ou Bedrock e se tem uma cinematic Bedrock ativa.

No modo proxy, o estado começa por indicar que está à espera do Velocity. Depois de um jogador Bedrock entrar, deve mostrar a ponte ligada e o catálogo de sons sincronizado. Se continuar à espera, confirma o JAR do Velocity, `send-floodgate-data` e a `key.pem`.

## Compatibilidade de som

O fluxo no modo proxy é:

```text
ação normal do Typewriter/BasicExtension no Paper
  -> pacote Java SOUND_EFFECT ou ENTITY_SOUND_EFFECT
  -> PacketEvents confirma uma cinematic Bedrock ativa
  -> catálogo do Geyser-Velocity confirma o identificador
  -> mensagem segura enviada pelo canal do jogador atual
  -> PlaySoundPacket enviado pela sessão Geyser no Velocity
```

O pacote Java só é cancelado depois de a ponte aceitar o envio. Um jogador Java nunca entra neste caminho. Se o som não existir, o proxy não estiver pronto ou uma chamada interna do Geyser mudar, o pacote original continua para o tradutor normal do Geyser.

O catálogo lê packs descompactados e ficheiros `.zip` ou `.mcpack`. Para um identificador Java como `historia:intro`, procura, por ordem:

- `historia:intro`
- `historia.intro`
- `historia/intro`

Para sons do namespace `minecraft`, também tenta o nome sem `minecraft:`. A comparação ignora maiúsculas e minúsculas, mas o identificador original do pack é enviado ao cliente.

Um pack produzido pelo Scaffolding funciona desde que o resultado Bedrock contenha `sounds/sound_definitions.json`. A extensão não converte packs Java para Bedrock. Depois de alterar os packs, reinicia ou recarrega o componente onde está o Geyser. No modo Velocity, os jogadores Bedrock devem voltar a ligar para receber o pack e o catálogo novo é sincronizado automaticamente com o Paper.

## Compatibilidade e segurança

O controlo do HUD usa a API pública do Geyser. O envio direto de som é a única integração interna e fica isolado através de reflexão. Uma incompatibilidade desativa o áudio direto sem impedir o arranque e sem remover o fallback normal.

No Velocity, as mensagens do canal são sempre marcadas como tratadas, só são aceites quando vêm do backend atual do próprio jogador e têm limites de tamanho. Ao mudar de backend, sair ou desligar o proxy, qualquer HUD controlado pela ponte é restaurado.

O encaminhamento moderno do Velocity ajuda a autenticar os dados enviados ao Paper, mas não substitui uma firewall. Mantém os backends inacessíveis ao público sempre que possível.

## Testes

A verificação completa é executada por `./gradlew clean build --no-daemon`. Os testes automatizados cobrem:

- início, troca, fim, saída e limpeza global de cinematics;
- restauro exato do HUD, incluindo falhas e chamadas repetidas;
- codificação, limites e divisão do protocolo Paper/Velocity;
- sincronização do catálogo remoto;
- leitura de packs descompactados, `.zip` e `.mcpack`;
- resolução de nomes e fallback quando o som ou o transporte não estão disponíveis.

Antes de publicar, faz ainda uma validação com clientes reais:

1. Confirma que os dois JARs arrancam sem erros e que `/tw bedrockbridge` mostra a ponte ligada.
2. Confirma que uma cinematic Java mantém imagem, HUD e áudio inalterados.
3. Inicia, termina e cancela uma cinematic Bedrock e confirma o restauro do HUD.
4. Muda o jogador Bedrock de backend e sai durante uma cinematic.
5. Executa `/typewriter reload` durante outra cinematic.
6. Toca um som personalizado presente no pack e confirma posição, volume e pitch.
7. Repete com um som ausente ou pack inválido e confirma o fallback e os avisos da consola.

Os testes unitários não conseguem simular uma sessão de rede nem um cliente Bedrock real, por isso esta validação manual continua a ser necessária.

## Referências

- [Criar e instalar extensões Typewriter](https://docs.typewritermc.com/develop/extensions/getting_started)
- [Instalar o Typewriter e o PacketEvents](https://docs.typewritermc.com/docs/getting-started/installation)
- [API oficial do Geyser](https://geysermc.org/wiki/geyser/api/)
- [Configurar o Floodgate numa rede com proxy](https://geysermc.org/wiki/floodgate/setup/proxy-servers/)
- [API do Floodgate num backend](https://geysermc.org/wiki/floodgate/api/)
- [Mensagens de plugin no Velocity](https://docs.papermc.io/velocity/dev/plugin-messaging/)
- [Segurança dos backends Velocity](https://docs.papermc.io/velocity/security/)
