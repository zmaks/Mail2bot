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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Maksim on 12.02.2017.
 */
public class MailTeleBot extends TelegramLongPollingBot implements MessageListener {
    final static Logger LOGGER = Logger.getLogger(MailTeleBot.class);

    private static final Long ADMIN_CHAT_ID = 183375382L;
    private static final String START_MESSAGE = "Привет! Чтобы подключтиься к Вашей почте и приступить к работе, мне необходимы адрес почты (доступна только Яндекс.Почта) и пароль через пробел. Например:\n\nivanov@ya.ru password123";
    private static final String SERVICE_STARTED = "Работа уже идет полным ходом! :)";
    private static final String SHUTDOWN_MESSAGE = "К сожалению, я вынужден остановить работу. Это связано с неполадками на сервере. Отправьте команду /start сейчас и как только все наладится, я Вам отвечу. \n\nДля ускорения возобновления работы, напишите разработчику: @z_maks";
    private static final String STOPPED_BY_ADMIN = "Работа остановлена администратором.";
    private static final String STOP_MESSAGE = "Пересылка картинок остановлена. Для возобновления работы воспользуйтесь командой /start.";
    private static final String ALREADY_STARTED = "Сервис еще не запущен, останавливать нечего. Воспользуйтесь командой /start :)";
    private static final String CONNECTING = "Подключаюсь...";
    private static final String AUTH_FAILED = "Не удалось подключиться.\nПроверьте корректность адреса и пароля и отправьте их заново.";
    private static final String CONNECTION_SUCCESS = "Готово! Теперь все фотографии, приходящие на Вашу почты будут пересылаться в этот диалог.\nЧтобы остановить процесс, отправьте команду /stop.";

    private static final String USER_CONNECTED = "Подключился пользователь ";
    private static final String INCORRECT_PARAMS = "Адрес почты и пароль введены неправильно. Пришлите, пожалуйста, правильныные адрес почты и пароль через пробел. Например:\n\n ivanov@ya.ru password123";
    private Map<Long, MailService> serviceMap = new HashMap<Long, MailService>();

    private Pattern mailParamsPattern = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\s.{2,}");

    public MailTeleBot(){
        super();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                for (Long chatId: serviceMap.keySet()) {
                    try {
                        sendMessage(new SendMessage()
                                .setChatId(chatId)
                                .setText(SHUTDOWN_MESSAGE)
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
        stringBuilder.append("id: ");
        stringBuilder.append(chatId);
        return stringBuilder.toString();
    }

    private void handleMessage(Long chatId, String text, String from) throws TelegramApiException {
        if (chatId.equals(ADMIN_CHAT_ID) && text.contains(Commands.SEND)) {
            LOGGER.info("SEND command");
            performMessageByAdmin(text);
        }
        if (chatId.equals(ADMIN_CHAT_ID) && text.contains(Commands.ADMIN_STOP)) {
            LOGGER.info("ADMIN STOP command");
            performAdminStop(text);
        }
        if(text.equals(Commands.START)){
            LOGGER.info("START command ["+chatId+"]");
            performStartCommand(chatId);
            //System.out.println("START");
            return;
        }
        if (text.equals(Commands.RESTART)) {
            LOGGER.info("RESTART command ["+chatId+"]");
            performStopCommand(chatId, true);
            performStartCommand(chatId);
            return;
        }
        if (text.equals(Commands.STOP)){
            LOGGER.info("STOP command ["+chatId+"]");
            performStopCommand(chatId, false);
            return;
        }
        if (serviceMap.containsKey(chatId) && serviceMap.get(chatId) == null) {
            LOGGER.info("CONNECTION command ["+chatId+"]");
            performConnection(chatId, text, from);
            return;
        }

    }

    private void performAdminStop(String text) throws TelegramApiException {
        String[] params = text.split(" ");
        if (params.length == 2) {
            performStopCommand(Long.parseLong(params[1]), true);
            sendMessage(new SendMessage()
                    .setChatId(params[1])
                    .setText(STOPPED_BY_ADMIN)
            );
            sendToAdmin("Работа пользователя " + params[1] + " остановлена.");
            LOGGER.info(params[1] + " stopped by admin");
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
            serviceMap.get(chatId).stop(false);
            serviceMap.remove(chatId);
            message = STOP_MESSAGE;
        } else
            message = ALREADY_STARTED;
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
        Matcher matcher = mailParamsPattern.matcher(text);
        if (matcher.find()) {

            String[] parts = matcher.group().split(" ");
            sendMessage(new SendMessage()
                            .setChatId(chatId)
                            .setText(CONNECTING)
            );
            LOGGER.info("Performing connection to " + parts[0]);

            try {
                addMailService(chatId, parts[0], parts[1]);
            } catch (MessagingException e) {
                e.printStackTrace();
                sendMessage(new SendMessage()
                        .setChatId(chatId)
                        .setText(AUTH_FAILED)
                );
                LOGGER.error("CONNECTION ERROR in ["+chatId+"]", e);
                return;
            }
            message = CONNECTION_SUCCESS;
            sendToAdmin(USER_CONNECTED + from);
            LOGGER.info("Connection is success!");
        } else {
            message = INCORRECT_PARAMS;
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
