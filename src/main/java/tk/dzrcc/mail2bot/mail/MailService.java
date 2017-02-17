package tk.dzrcc.mail2bot.mail;

import org.apache.log4j.Logger;
import tk.dzrcc.mail2bot.Utils;

import javax.mail.*;
import javax.mail.internet.MimeBodyPart;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

/**
 * Created by Maksim on 12.02.2017.
 */
public class MailService {
    final static Logger LOGGER = Logger.getLogger(MailService.class);

    private String host;
    private String username;
    private String password;
    private Long ownerChatId;
    private Properties props;
    private Date lastMessageDate;

    private MessageListener bot;
    private Thread mailUpdateThread;

    private Store store;
    private Folder inbox;

    private static final Long FREQUENCY = 10000L;
    private int messagesCount = -1;

    private static final String PROVIDER = "imap";

    public MailService() {
        props = new Properties();
        props.setProperty("mail.imap.starttls.enable", "true");
        props.put("mail.imap.auth", "true");
        props.put("mail.imap.socketFactory.port", "993");
        props.put("mail.imap.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        props.put("mail.imap.socketFactory.fallback", "false");
    }

    public void connect() throws MessagingException {
        //Utils.checkParam(host, "хост");
        Utils.checkParam(username, "email");
        Utils.checkParam(password, "пароль");
        host = username2host(username);
        LOGGER.info("Host:" + host);
        Session session = Session.getDefaultInstance(props, null);
        store = session.getStore(PROVIDER);
        store.connect(host, username, password);
        if (store.isConnected()){
            startMailUpdate();
        }
    }

    private String username2host(String username){
        String mail = username.split("@")[1];
        if (mail.equals("ya.ru")) mail = "yandex.ru";
        if (mail.equals("inbox.ru")) mail = "mail.ru";
        if (mail.equals("bk.ru")) mail = "mail.ru";
        if (mail.equals("list.ru")) mail = "mail.ru";
        return "imap." + mail;
    }

    private void reopenFolder() throws MessagingException {
        if (inbox.isOpen()) inbox.close(false);
        inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);
    }

    private void startMailUpdate() throws MessagingException {
        inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);

        mailUpdateThread = new Thread(new MailUpdate());
        mailUpdateThread.setName("UT" + ownerChatId);
        mailUpdateThread.start();
        LOGGER.info("Thread " + mailUpdateThread.getName() + " started");
        System.out.println("Thread " + mailUpdateThread.getName() + " started");
    }

    private synchronized void performNewMails(Folder inbox, int oldMessagesCount, int newMessageCount) throws MessagingException, IOException {
        Message[] messages = inbox.getMessages(oldMessagesCount + 1, newMessageCount);

        for(Message message : messages) {
            if(message != null) {
                parseMessage(message);
            }
        }
    }

    private synchronized void performNewMailsWithDeleted(Folder inbox, int newMessageCount) throws MessagingException, IOException {
        int i = 1;
        Message message = inbox.getMessage(newMessageCount - i);
        Date lastDate = lastMessageDate;
        while (lastDate.compareTo(message.getReceivedDate()) < 0) {
            parseMessage(message);
            i++;
            message = inbox.getMessage(newMessageCount - i);
        }
    }

    private void parseMessage(Message message) throws MessagingException, IOException {
        String contentType = "";
        try {
            contentType = message.getContentType();
        } catch (NullPointerException e) {
            e.printStackTrace();
            return;
        }
        //System.out.println(contentType);
        if (contentType.toLowerCase().contains("multipart")) {
            Multipart multiPart = (Multipart) message.getContent();
            int numberOfParts = multiPart.getCount();

            for (int partCount = 0; partCount < numberOfParts; partCount++) {
                MimeBodyPart part = (MimeBodyPart) multiPart.getBodyPart(partCount);
                LOGGER.info("Part "+(partCount+1)+" disposition: " + part.getDisposition());
                LOGGER.info("Part "+(partCount+1)+" content type: " + part.getContentType());

                if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())){
                    InputStream inputStream = part.getInputStream();
                    if (inputStream == null) {
                        //System.out.println("INPUT STREAM IS NULL");
                        LOGGER.info("InputStream is NULL");
                    }
                    else {
                        LOGGER.info("Sending image to bot");
                        bot.sendToTelegram("image.jpeg", inputStream, ownerChatId);
                    }
                    lastMessageDate = message.getReceivedDate();
                }
            }
        }
    }

    private class MailUpdate implements Runnable {
        public void run() {
            int i = 0;
            try {
                messagesCount = inbox.getMessageCount();
                LOGGER.info("Message count: " + messagesCount);
                //System.out.println("1st count:" + messagesCount);
                while (true) {


                    reopenFolder();
                    int newMessageCount = inbox.getMessageCount();
                    LOGGER.info(Thread.currentThread().getName()+" " +i+" ------------------------ " + messagesCount);
                    if (newMessageCount > messagesCount) {
                        performNewMails(inbox, messagesCount, newMessageCount);
                    }
                    if (newMessageCount < messagesCount) {
                        performNewMailsWithDeleted(inbox, newMessageCount);
                    }
                    messagesCount = newMessageCount;
                    i++;
                    Thread.sleep(FREQUENCY);
                }

            } catch (MessagingException | IOException | InterruptedException e) {
                LOGGER.error("Updating mails error in [" + Thread.currentThread().getName() + "]", e);
                reconnect();
                e.printStackTrace();
            }

        }
    }

    private void reconnect(){
        bot.sendToTelegram("Ошибка при обновлении списка писем на почте.\nПопытаюсь переподключиться через 20 секунд...", ownerChatId);
        Thread currentThread = mailUpdateThread;
        stop(true);
        inbox = null;
        store = null;
        try {
            Thread.sleep(30000L);
            connect();
        } catch (MessagingException | InterruptedException e) {
            LOGGER.error("\n\n\nTOTAL FAIL\n\nReconnection error!", e);
            bot.sendToTelegram("Ошибка обновления почты, переподключиться не удалось. Возможно, сменен пароль почты. Попробуйте подключиться позже с помощью команды /restart.", ownerChatId);
            bot.sendToAdmin("Ошибка обновления у пользователя " + ownerChatId);
            e.printStackTrace();
            return;
        }
        bot.sendToTelegram("Переподключение прошло успешно! Продолжаю работу.", ownerChatId);
    }

    public void stop(boolean isReconnect){
        if(mailUpdateThread != null && mailUpdateThread.isAlive() && !isReconnect) {
            mailUpdateThread.stop();
            LOGGER.info(mailUpdateThread.getName() + " stopped");
        }

        try {
            if (inbox != null && inbox.isOpen()) {
                inbox.close(false);
                LOGGER.info("Inbox closed");
            }
            if (store != null && store.isConnected()) {
                store.close();
                LOGGER.info("Store closed");
            }
        } catch (MessagingException e) {
            LOGGER.error("Stop thread error " + e);
            e.printStackTrace();
        }
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setBot(MessageListener bot) {
        this.bot = bot;
    }

    public Long getOwnerChatId() {
        return ownerChatId;
    }

    public void setOwnerChatId(Long ownerChatId) {
        this.ownerChatId = ownerChatId;
    }
}
