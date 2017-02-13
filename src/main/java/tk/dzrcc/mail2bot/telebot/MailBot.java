package tk.dzrcc.mail2bot.telebot;

import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.methods.send.SendPhoto;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import tk.dzrcc.mail2bot.mail.MailService;
import tk.dzrcc.mail2bot.mail.MessageListener;

import javax.mail.MessagingException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Maksim on 12.02.2017.
 */
public class MailBot extends TelegramLongPollingBot implements MessageListener {
    private static final String START_MESSAGE = "Привет! Чтобы подключтиься к Вашей почте и приступить к работе, мне необходимы хост, адрес почты и пароль (через пробел). Например:\n\nimap.yandex.ru ivanov@yandex.ru Qwerty123";
    private Map<Long, MailService> serviceMap = new HashMap<Long, MailService>();

    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            try {
                handleMessage(update.getMessage().getChatId(), update.getMessage().getText());
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleMessage(Long chatId, String text) throws TelegramApiException {
        if(text.equals(Commands.STRAT)){
            sendMessage(new SendMessage()
                    .setChatId(chatId)
                    .setText(START_MESSAGE)
            );
        }
        if(!text.equals(Commands.STRAT)){
            String[] parts = text.split(" ");
            if (parts.length == 2) {
                MailService ms = addMailService(chatId, parts[0], parts[1]);
                //ms.setUsername(parts[0]);
                //ms.setPassword(parts[1]);
                try {
                    ms.connect();
                } catch (MessagingException e) {
                    sendMessage(new SendMessage()
                            .setChatId(chatId)
                            .setText("Не получилось подключиться.\n"+e.getMessage())
                    );
                    e.printStackTrace();
                }
                sendMessage(new SendMessage()
                        .setChatId(chatId)
                        .setText("Ok")
                );
            } else {
                sendMessage(new SendMessage()
                        .setChatId(chatId)
                        .setText("Не то")
                );
            }
        }
    }

    private MailService addMailService(Long chatId, String username, String pass){
        MailService ms = new MailService();
        ms.setUsername(username);
        ms.setPassword(pass);
        ms.setOwnerChatId(chatId);
        ms.setBot(this);
        if(serviceMap.containsKey(chatId)){
            serviceMap.get(chatId).stop();
        }
        serviceMap.put(chatId, ms);
        return ms;
    }

    public synchronized void sendToTelegram(String name, InputStream image, Long chatId) {
        try {
            sendPhoto(new SendPhoto().setNewPhoto(name, image).setChatId(chatId));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public String getBotUsername() {
        return "mail2bot";
    }

    public String getBotToken() {
        return "302651007:AAFwstZCWKmYIgYL3txx4HMp0QMNO9Y7i3g";
    }
}
