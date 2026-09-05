# BedrockBridge

Extensão de compatibilidade transparente entre as cinematics do Typewriter e jogadores Bedrock ligados através do Geyser. Mantém o comportamento normal dos jogadores Java e não substitui ações do Typewriter ou da BasicExtension.

## Funcionalidades

- Deteta jogadores Bedrock através da API pública do Geyser.
- Oculta o HUD Bedrock durante uma cinematic e repõe exatamente o estado anterior no fim.
- Mantém o HUD oculto caso outra parte do Typewriter o reponha a meio da cinematic.
- Repõe o estado ao terminar ou trocar de cinematic, ao sair do servidor e durante unload/reload.
- Encaminha sons personalizados de cinematics diretamente para o cliente Bedrock quando o som existe num pack carregado pelo Geyser.
- Falha de forma segura: se o Geyser, o pack ou o caminho de som não estiverem disponíveis, o pacote Java original não é cancelado.
- Disponibiliza diagnóstico através de comandos e da consola.

## Requisitos

- JDK 21 para compilar e executar o Typewriter.
- Paper com Typewriter 0.8.0 e a BasicExtension correspondente.
- PacketEvents 2.7.0, a versão usada pelo Typewriter 0.8.0.
- Geyser-Spigot para as funcionalidades Bedrock. A extensão continua a carregar sem Geyser.

O projeto tem como alvo a linha Typewriter 0.8.x, mas a build está ligada à versão 0.8.0. As extensões do Typewriter devem acompanhar a versão instalada no servidor; valida e recompila antes de usar outra versão 0.8.x.

## Instalação

1. Executa `./gradlew clean build` ou, no Windows, `.\gradlew.bat clean build`.
2. Copia o JAR criado em `build/libs` para `plugins/Typewriter/extensions`.
3. Instala o Geyser-Spigot e coloca o pack Bedrock em `plugins/Geyser-Spigot/packs`.
4. Reinicia o servidor ou executa `/typewriter reload`.

Os JAR de extensões do Typewriter não devem ser colocados diretamente na pasta `plugins`.

## Diagnóstico

Os comandos requerem a permissão `typewriter.bedrockbridge.debug`.

- `/tw bedrockbridge` mostra o estado do Geyser, a versão da API, cinematics Bedrock ativas, sons encontrados, estado do transporte e quantidade de sons encaminhados.
- `/tw bedrockbridge check [jogador]` indica se o jogador é Java ou Bedrock e se tem uma cinematic Bedrock ativa.

No arranque, a consola também indica se o Geyser foi encontrado, quantas definições de som foram carregadas e se o listener de som ficou ativo.

## Compatibilidade de som

O caminho implementado é:

```text
ação normal do Typewriter/BasicExtension
  -> pacote Java SOUND_EFFECT ou ENTITY_SOUND_EFFECT
  -> observação pelo PacketEvents apenas para uma cinematic Bedrock ativa
  -> confirmação do identificador em sounds/sound_definitions.json
  -> PlaySoundPacket enviado diretamente pela sessão Geyser
```

O pacote Java só é cancelado depois de o `PlaySoundPacket` ter sido enviado com sucesso. Um jogador Java nunca entra neste caminho. Se não existir uma correspondência, ou se uma chamada interna do Geyser mudar, o pacote original continua para o tradutor normal do Geyser.

O catálogo lê packs descompactados e ficheiros `.zip` ou `.mcpack` dentro do diretório público de packs do Geyser. Para um identificador Java como `historia:intro`, procura, por ordem:

- `historia:intro`
- `historia.intro`
- `historia/intro`

Para sons do namespace `minecraft`, também tenta o nome sem `minecraft:`. A comparação ignora maiúsculas e minúsculas, mas o nome original do pack é enviado ao cliente.

Um pack produzido pelo Scaffolding funciona desde que o resultado Bedrock contenha `sounds/sound_definitions.json` e esteja instalado no diretório de packs do Geyser. Esta extensão não converte packs Java para Bedrock. Depois de alterar os packs, reinicia o servidor ou recarrega o Typewriter para reconstruir o catálogo; os clientes Bedrock devem voltar a ligar para receber alterações ao pack.

## Nota de compatibilidade com o Geyser

A deteção de jogadores, o diretório de packs e o controlo do HUD usam apenas a API pública do Geyser. O envio direto de som é a única integração interna e está isolado em `GeyserApiGateway` através de reflexão.

Este caminho foi verificado contra as dependências exatas do Typewriter 0.8.0:

- Geyser API `2.4.2-20240914.223501-32`;
- Geyser core `2.4.2-20240914.223501-31`, SHA-256 `5BC009C743A649C676C3AE17684301949ED91C72A67B6644A90FA627962892C8`;
- Cloudburst bedrock-codec `3.0.0.Beta4-20240828.162251-1`.

Nestas versões, `GeyserSession#sendUpstreamPacket(BedrockPacket)` aceita o `PlaySoundPacket` usado pelo próprio Geyser. A pesquisa e invocação são feitas em runtime. Uma incompatibilidade desativa o encaminhamento direto sem impedir o arranque da extensão e sem remover o fallback normal.

## Ciclo de vida e segurança

O `shutdown` remove os listeners Bukkit e PacketEvents e restaura todas as sessões de HUD. O mesmo tratamento é aplicado ao evento de unload do Typewriter, incluindo reloads forçados que não produzam o evento normal de fim da cinematic. As operações de limpeza são idempotentes para poderem ser chamadas mais de uma vez com segurança.

## Testes

Executa a verificação completa com:

```shell
./gradlew clean build --no-daemon
```

Os testes automatizados cobrem:

- início, troca, fim, saída e limpeza global de cinematics;
- restauro exato do HUD, incluindo falhas e chamadas repetidas;
- arranque sem Geyser;
- leitura de packs descompactados, `.zip` e `.mcpack`, incluindo ficheiros inválidos;
- resolução de nomes de som e fallback quando o som ou o transporte não estão disponíveis.

O workflow de GitHub Actions repete a build com JDK 21 em cada push e pull request.

Antes de publicar num servidor, deve ainda ser feita uma validação manual com clientes reais:

1. Confirmar que a extensão arranca com e sem Geyser.
2. Confirmar que uma cinematic Java mantém imagem, HUD e áudio inalterados.
3. Iniciar, terminar e cancelar uma cinematic Bedrock e confirmar o restauro do HUD.
4. Sair durante uma cinematic e executar `/typewriter reload` durante outra.
5. Tocar um som personalizado presente no pack e confirmar posição, volume e pitch no Bedrock.
6. Repetir com um som ausente ou pack inválido e confirmar o fallback e os avisos da consola.

Os testes unitários e de build não conseguem simular uma sessão de rede nem um cliente Bedrock real, por isso esta matriz manual continua a ser necessária para validar o ambiente final do servidor.

## Referências

- [Criar e instalar extensões Typewriter](https://docs.typewritermc.com/develop/extensions/getting_started)
- [Instalar o Typewriter e o PacketEvents](https://docs.typewritermc.com/docs/getting-started/installation)
- [API oficial do Geyser](https://geysermc.org/wiki/geyser/api/)
- [Packs Bedrock no Geyser](https://geysermc.org/wiki/geyser/packs/)
