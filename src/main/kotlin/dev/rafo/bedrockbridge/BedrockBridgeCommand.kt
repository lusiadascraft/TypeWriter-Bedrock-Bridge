package dev.rafo.bedrockbridge

import com.typewritermc.core.extension.annotations.TypewriterCommand
import com.typewritermc.engine.paper.command.dsl.CommandTree
import com.typewritermc.engine.paper.command.dsl.executePlayerOrTarget
import com.typewritermc.engine.paper.command.dsl.sender
import com.typewritermc.engine.paper.command.dsl.withPermission
import com.typewritermc.engine.paper.logger

@TypewriterCommand
fun CommandTree.bedrockBridgeCommand() = literal("bedrockbridge") {
    withPermission("typewriter.bedrockbridge.debug")

    executes {
        val status = BedrockBridge.status()
        val apiVersion = status.geyserApiVersion?.let { ", API $it" }.orEmpty()
        sender.sendMessage(
            "BedrockBridge — Geyser: ${status.geyserStatus}$apiVersion; " +
                "cinematics Bedrock ativas: ${status.activeCinematics}.",
        )
    }

    literal("check") {
        executePlayerOrTarget { target ->
            val status = BedrockBridge.playerStatus(target.uniqueId)
            val platform = if (status.bedrockPlayer) "Bedrock" else "Java"
            val cinematic = if (status.cinematicActive) "ativa" else "inativa"
            val message = "BedrockBridge — ${target.name}: $platform; cinematic $cinematic."

            sender.sendMessage(message)
            logger.info("Diagnóstico pedido por ${sender.name}: $message")
        }
    }
}
