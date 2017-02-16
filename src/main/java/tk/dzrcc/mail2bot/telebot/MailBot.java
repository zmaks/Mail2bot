package tk.dzrcc.mail2bot.telebot;

import org.apache.log4j.Logger;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.methods.send.SendPhoto;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.api.objects.User;
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
    final static Logger LOGGER = Logger.getLogger(MailBot.class);


    private static final String START_MESSAGE = "Привет! Чтобы подключтиься к Вашей почте и приступить к работе, мне необходимы адрес почты (Яндекс.Почта или Gmail) и пароль через пробел. Например:\n\nivanov@ya.ru password123";
    private static final String SERVICE_STARTED = "Работа уже идет полным ходом! :)";
    private static final Long ADMIN_CHAT_ID = 183375382L;
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
                        LOGGER.info("Shutdown hook message sent");
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                        LOGGER.error("Error in sending message["+chatId+"]", e);
                    }
                }
            }
        });
    }

    public void onUpdateReceived(Update update) {
        update.getUpdateId();
        if (update.hasMessage() && update.getMessage().hasText()) {
            String from = getFromString(update.getMessage().getFrom(), update.getMessage().getChatId());
            LOGGER.info("New message by " + from);
            //System.out.println(update.getMessage().getText());
            try {
                handleMessage(update.getMessage().getChatId(), update.getMessage().getText(), from);
            } catch (TelegramApiException e) {
                e.printStackTrace();
                LOGGER.error("Error in message handler["+update.getMessage().getChatId()+"]", e);
            }
        }
    }

    private String getFromString(User user, Long chatId) {
        StringBuilder stringBuilder = new StringBuilder();
        if (user.getFirstName() != null) {
            stringBuilder.append(user.getFirstName());
            stringBuilder.append(" ");
        }
        if (user.getLastName() != null) {
            stringBuilder.append(user.getLastName());
            stringBuilder.append(" ");
        }
        if (user.getUserName() != null) {
            stringBuilder.append(user.getUserName());
            stringBuilder.append(" ");
        }
        stringBuilder.append("\nid: ");
        stringBuilder.append(chatId);
        return stringBuilder.toString();
    }

    private void handleMessage(Long chatId, String text, String from) throws TelegramApiException {
        if (chatId.equals(ADMIN_CHAT_ID) && text.contains(Commands.SEND)) {
            performMessageByAdmin(text);
        }
        if(text.equals(Commands.START)){
            performStartCommand(chatId);
            //System.out.println("START");
            LOGGER.info("START command ["+chatId+"]");
            return;
        }
        if (text.equals(Commands.RESTART)) {
            performStopCommand(chatId, true);
            performStartCommand(chatId);
            LOGGER.info("RESTART command ["+chatId+"]");
            return;
        }
        if (text.equals(Commands.STOP)){
            performStopCommand(chatId, false);
            LOGGER.info("STOP command ["+chatId+"]");
            return;
        }
        if (serviceMap.containsKey(chatId) && serviceMap.get(chatId) == null) {
            performConnection(chatId, text, from);
            return;
        }

    }

    private void performMessageByAdmin(String text) throws TelegramApiException {
        String[] params = text.split("&");
        if (params.length == 3) {
            sendMessage(new SendMessage()
                    .setChatId(params[1])
                    .setText(params[2])
            );
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
        if (!isRestart) {
            sendMessage(new SendMessage()
                    .setChatId(chatId)
                    .setText(message)
            );
            LOGGER.info("STOP message sent to " + chatId);
        }
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
        LOGGER.info("START message sent to " + chatId);
    }

    private void performConnection(Long chatId, String text, String from) throws TelegramApiException {
        String message = "";
        String[] parts = text.split(" ");
        if (mailParamsPattern.matcher(text).find() && parts.length == 2) {
            sendMessage(new SendMessage()
                            .setChatId(chatId)
                            .setText("Подключаюсь...")
            );
            LOGGER.info("Performing connection to " + parts[0]);

            try {
                addMailService(chatId, parts[0], parts[1]);
            } catch (MessagingException e) {
                e.printStackTrace();
                sendMessage(new SendMessage()
                        .setChatId(chatId)
                        .setText("Не удалось подключиться.\nПроверьте корректность адреса и пароля и отправьте их заново.")
                );
                LOGGER.error("CONNECTION ERROR in ["+chatId+"]", e);
                return;
            }
            message = "Готово! Теперь все фотографии, приходящие на Вашу почты будут пересылаться в этот диалог.\nЧтобы остановить процесс, отправьте команду /stop.";
            sendToAdmin("Подключился пользователь " + from);
            LOGGER.info("Connection is success!");
        } else {
            message = "Адрес почты и пароль введены неправильно. Пришлите, пожалуйста, правильныные адрес почты и пароль через пробел. Например:\n\n ivanov@ya.ru password123";
            LOGGER.info("Wrong connection params by ["+chatId+"]");
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
            LOGGER.info("Photo successfully sent by service ["+chatId+"]");
        } catch (TelegramApiException | IOException e) {
            e.printStackTrace();
            LOGGER.error("Error in sending photo by service ["+chatId+"]", e);
        }
    }

    @Override
    public void sendToTelegram(String text, Long chatId) {
        try {
            sendMessage(new SendMessage()
                    .setChatId(chatId)
                    .setText(text)
            );
            LOGGER.info("Message successfully sent by service ["+chatId+"]");
        } catch (TelegramApiException e) {
            e.printStackTrace();
            LOGGER.error("Error in sending message by service ["+chatId+"]", e);
        }
    }

    @Override
    public void sendToAdmin(String text) {
        try {
            sendMessage(new SendMessage()
                    .setChatId(ADMIN_CHAT_ID)
                    .setText(text)
            );
            LOGGER.info("Message successfully sent by service to admin");
        } catch (TelegramApiException e) {
            e.printStackTrace();
            LOGGER.error("Error in sending message to admin by service", e);
        }
    }

    public String getBotUsername() {
        return "mail2bot";
    }

    public String getBotToken() {
        return "302651007:AAFwstZCWKmYIgYL3txx4HMp0QMNO9Y7i3g";
    }
}
