package tk.dzrcc.mail2bot.telebot;

import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.methods.send.SendPhoto;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import tk.dzrcc.mail2bot.mail.MailService;
import tk.dzrcc.mail2bot.mail.MessageListener;

import javax.mail.MessagingException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by Maksim on 12.02.2017.
 */
public class MailBot extends TelegramLongPollingBot implements MessageListener {
    private static final String START_MESSAGE = "Привет! Чтобы подключтиься к Вашей почте и приступить к работе, мне необходимы адрес почты (пока доступна только Яндекс.Почта) и пароль через пробел. Например:\n\nivanov@ya.ru password123";
    private static final String SERVICE_STARTED = "Вы уже запустили сервис :)";
    private Map<Long, MailService> serviceMap = new HashMap<Long, MailService>();

    private Pattern mailParamsPattern = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\s.{2,}$");

    public MailBot(){
        super();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                for (Long chatId: serviceMap.keySet()) {
                    try {
                        sendMessage(new SendMessage()
                                .setChatId(chatId)
                                .setText("К сожалению, я вынужден остановить работу. Это связано с неполадками на сервере. Отправьте команду /start сейчас и как только все наладится, я Вам отвечу. \n\nДля ускорения возобновления работы, напишите разработчику: @z_maks")
                        );
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

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
        if(text.equals(Commands.START)){
            performStartCommand(chatId);
            return;
        }
        if (text.equals(Commands.RESTART)) {
            performStopCommand(chatId, true);
            performStartCommand(chatId);
            return;
        }
        if (text.equals(Commands.STOP)){
            performStopCommand(chatId, false);
            return;
        }
        if (serviceMap.containsKey(chatId) && serviceMap.get(chatId) == null) {
            performConnection(chatId, text);
            return;
        }
    }

    private void performStopCommand(Long chatId, boolean isRestart) throws TelegramApiException {
        String message = "";
        if (serviceMap.containsKey(chatId) && serviceMap.get(chatId) != null) {
            serviceMap.get(chatId).stop();
            serviceMap.remove(chatId);
            message = "Пересылка картинок остановлена. Для возобновления работы воспользуйтесь командой /start.";
        } else
            message = "Сервис еще не запущен, останавливать нечего. Воспользуйтесь командой /start :)";
        if (!isRestart)
            sendMessage(new SendMessage()
                    .setChatId(chatId)
                    .setText(message)
            );
    }

    private void performStartCommand(Long chatId) throws TelegramApiException {
        String message;
        if (serviceMap.containsKey(chatId) && serviceMap.get(chatId) != null) {
            message = SERVICE_STARTED;
        } else {
            message = START_MESSAGE;
            serviceMap.put(chatId, null);
        }
        sendMessage(new SendMessage()
                .setChatId(chatId)
                .setText(message)
        );
    }

    private void performConnection(Long chatId, String text) throws TelegramApiException {
        String message = "";
        if (mailParamsPattern.matcher(text).find()) {
            sendMessage(new SendMessage()
                            .setChatId(chatId)
                            .setText("Подключаюсь...")
            );
            String[] parts = text.split(" ");
            MailService ms;
            try {
                ms = addMailService(chatId, parts[0], parts[1]);
            } catch (MessagingException e) {
                e.printStackTrace();
                sendMessage(new SendMessage()
                        .setChatId(chatId)
                        .setText("Не удалось подключиться.\nПроверьте корректность адреса и пароля и отправьте их заново.")
                );
                return;
            }
            message = "Готово! Теперь все фотографии, приходящие на Вашу почты будут пересылаться в этот диалог.\nЧтобы остановить процесс, отправьте команду /stop. \n\nВнимание! Советую удалить Ваше сообщение с паролем от почты, чтобы его никто не узнал.";
        } else {
            message = "Вы еще не ввели адрес почты и пароль. Или же ввели неправильно. Пришлите, пожалуйста, правильныные адрес почты и пароль через пробел. Например:\n\n ivanov@ya.ru password123";
        }
        sendMessage(new SendMessage()
                .setChatId(chatId)
                .setText(message)
        );
    }

    private MailService addMailService(Long chatId, String username, String pass) throws MessagingException {
        MailService ms = new MailService();
        ms.setUsername(username);
        ms.setPassword(pass);
        ms.setOwnerChatId(chatId);
        ms.setBot(this);
        ms.connect();
        /*if(serviceMap.containsKey(chatId)){
            serviceMap.get(chatId).stop();
        }*/
        serviceMap.put(chatId, ms);
        return ms;
    }

    public synchronized void sendToTelegram(String name, InputStream image, Long chatId) {
        try {
            sendPhoto(new SendPhoto().setNewPhoto(name, image).setChatId(chatId));
            image.close();
        } catch (TelegramApiException | IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void sendToTelegram(String text, Long chatId) {
        try {
            sendMessage(new SendMessage()
                    .setChatId(chatId)
                    .setText(text)
            );
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
