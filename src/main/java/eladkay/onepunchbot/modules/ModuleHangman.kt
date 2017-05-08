package eladkay.onepunchbot.modules

import de.btobastian.javacord.DiscordAPI
import de.btobastian.javacord.entities.Channel
import de.btobastian.javacord.entities.message.Message
import de.btobastian.javacord.entities.message.embed.EmbedBuilder
import eladkay.onepunchbot.IModule
import eladkay.onepunchbot.LargeStringHolder
import java.awt.Color
import java.util.*

/**
 * Created by Elad on 4/15/2017.
 */
object ModuleHangman : IModule {
    class Hangman(val word: String, val creator: String) {
        companion object {
            private val alphabet = (0 until 26).map { (it + 0x61).toChar() }.joinToString("")
        }

        var lastMessage: Message? = null

        fun update(channel: Channel) {
            val last = lastMessage
            last?.delete()
            channel.sendMessage("", createMessage())
        }

        fun handleResult(message: Message, result: EnumResult) {
            when (result) {
                ModuleHangman.Hangman.EnumResult.LOSS -> {
                    message.reply(LargeStringHolder.LOSS)
                    message.reply("Phrase: $word")
                    endAndQueue(message.channelReceiver)
                }
                ModuleHangman.Hangman.EnumResult.WIN -> {
                    message.reply(LargeStringHolder.CORRECT)
                    message.reply("Phrase: $word")
                    endAndQueue(message.channelReceiver)
                }
                ModuleHangman.Hangman.EnumResult.CONTINUE -> update(message.channelReceiver)
            }
        }

        fun createMessage(): EmbedBuilder {
            val embedBuilder = EmbedBuilder()
            embedBuilder.setTitle("Hangman")
            embedBuilder.addField("Guessed Letters", guessedLetters, true)
            embedBuilder.addField("Guessed Phrases", guessedPhrases, true)
            embedBuilder.addField("Hangman", toString(), true)
            embedBuilder.addField("Word", "``$wordWithUnderscores``", true)
            embedBuilder.setColor(Color(0x71d685))
            return embedBuilder
        }

        var stage: EnumHangmanStage = EnumHangmanStage.NO_MAN
        val guessed: MutableList<Char> = mutableListOf()
        val phrases: MutableList<String> = mutableListOf()
        val lowerPhrases: MutableList<String> = mutableListOf()

        val lowerWord = word.toLowerCase()

        fun advance(): Boolean {
            if (stage.ordinal == EnumHangmanStage.values().size - 1)
                return false
            else
                stage = EnumHangmanStage.values()[stage.ordinal + 1]

            return true
        }

        val wordWithUnderscores: String
            get() = word.map {if (!it.isLetter() || it.toLowerCase() in guessed) it.toString() else "_" }.joinToString("")

        enum class EnumResult {
            CONTINUE, LOSS, WIN
        }

        fun addChar(char: Char): EnumResult {
            if (!char.isLetter() || char.toLowerCase() in guessed) return EnumResult.CONTINUE

            if (char.toLowerCase() !in guessed)
                guessed.add(char.toLowerCase())

            if (char.toLowerCase() !in lowerWord) {
                if (advance())
                    return EnumResult.CONTINUE
                else
                    return EnumResult.LOSS
            }

            if (wordWithUnderscores == word) return EnumResult.WIN

            return EnumResult.CONTINUE
        }

        fun guessPhrase(phrase: String): EnumResult {
            if (phrase.toLowerCase() in lowerPhrases) return EnumResult.CONTINUE

            if (phrase.toLowerCase() !in lowerPhrases) {
                lowerPhrases.add(phrase.toLowerCase())
                phrases.add(phrase)
            }

            if (word.toLowerCase() != phrase.toLowerCase()) {
                if (advance())
                    return EnumResult.CONTINUE
                else
                    return EnumResult.LOSS
            } else return EnumResult.WIN
        }

        val guessedLetters: String
            get() = alphabet.map { if (it in guessed) it.toString() else "~~$it~~" }.joinToString("")

        val guessedPhrases: String
            get() = phrases.joinToString("\n")

        override fun toString(): String {
            return "${LargeStringHolder.HANGMAN_1}${stage.man.joinToString("\n")}\n${LargeStringHolder.HANGMAN_2}"
        }
    }


    fun endAndQueue(channel: Channel) {
        val hangmanObj = hangman[channel]
        hangmanObj?.lastMessage?.delete()
        hangman.remove(channel)

        if (q.getOrPut(channel) { ArrayDeque() }.peek() != null) {
            val newHangmanObj = q.getOrPut(channel) { ArrayDeque() }.poll()!!
            hangman.put(channel, newHangmanObj)
            channel.sendMessage("\n${newHangmanObj.creator} has started a game of Hangman!")
            Thread.sleep(500)
            newHangmanObj.update(channel)
        }
    }



    val hangman = mutableMapOf<Channel, Hangman>()
    val q = mutableMapOf<Channel, Queue<Hangman>>()

    override fun onMessage(api: DiscordAPI, message: Message): Boolean {
        if (message.userReceiver != null && message.content.startsWith("!hangman ")) {
            val args = message.content.split(" ")
            if (args.size < 3) {
                message.reply("Invalid! use: !hangman <channelid> <word>")
            } else {
                val id = args[1]
                val word = args.subList(2, args.size).joinToString(" ")
                if ("@" in word) {
                    message.reply("@ tags are not permitted for hangman words, because of possible abuse. Please try again.")
                    return super.onMessage(api, message)
                } else if (word.none { it.isLetter() }) {
                    message.reply("Your hangman doesn't have any letters to guess! Please try again.")
                    return super.onMessage(api, message)
                }
                val server = id.split("@")[1]
                val channel = id.split("@")[0]
                val channelobj = api.getServerById(server).getChannelById(channel)
                if (hangman[channelobj] == null) {
                    val hangmanObj = Hangman(word, message.author.name)
                    hangman.put(channelobj, hangmanObj)
                    message.reply("$word\n\nThis hangman is now running on $channelobj.")
                    channelobj.sendMessage("${message.author.name} has started a game of Hangman!")
                    hangmanObj.update(channelobj)
                } else {
                    val queue = q.getOrPut(channelobj) {
                        ArrayDeque()
                    }
                    val position = queue.size + 1
                    queue.add(Hangman(word, message.author.name))
                    message.reply("$word\n\nThis hangman has now been queued on $channelobj. Position on queue: $position")
                }
            }

        } else if (message.channelReceiver != null && message.channelReceiver in hangman && message.content.startsWith("!hangman ")) {
            message.delete()
            if (message.content.replace("!hangman ", "").isNotEmpty()) {
                val letter = message.content.replace("!hangman", "").trim()[0]
                if (letter.isLetter()) {
                    val hangmanObj = hangman[message.channelReceiver]
                    hangmanObj?.handleResult(message, hangmanObj.addChar(letter))
                }

            }
        } else if (message.channelReceiver != null && message.channelReceiver in hangman && message.content.startsWith("!guess ")) {
            message.delete()
            val hangmanObj = hangman[message.channelReceiver]
            val phrase = message.content.replace("!guess", "").trim()
            hangmanObj?.handleResult(message, hangmanObj.guessPhrase(phrase))
        }
        return super.onMessage(api, message)
    }

    enum class EnumHangmanStage(vararg val man: String) {
        NO_MAN(
                "  |",
                "  |",
                "  |"
        ), HEAD(
                "  |                   O",
                "  |",
                "  |"
        ), BODY(
                "  |                   O",
                "  |                    |",
                "  |"
        ), RIGHT_ARM(
                "  |                  O",
                "  |                   |\\",
                "  |"
        ), LEFT_ARM(
                "  |                  O",
                "  |                 /|\\",
                "  |"
        ), LEFT_LEG(
                "  |                  O",
                "  |                 /|\\",
                "  |                  /"
        ), RIGHT_LEG(
                "  |                  O",
                "  |                 /|\\",
                "  |                  /\\"
        )
    }
}
