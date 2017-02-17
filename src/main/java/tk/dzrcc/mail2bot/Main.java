package tk.dzrcc.mail2bot;

import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import tk.dzrcc.mail2bot.telebot.MailTeleBot;

/**
 * Created by Maksim on 12.02.2017.
 */
public class Main {
    public static void main(String[] args) {
        initBot();
    }

    private static  void  initBot() {
        ApiContextInitializer.init();

        TelegramBotsApi botsApi = new TelegramBotsApi();

        try {
            botsApi.registerBot(new MailTeleBot());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
