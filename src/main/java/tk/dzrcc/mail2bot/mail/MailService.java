package tk.dzrcc.mail2bot.mail;

import tk.dzrcc.mail2bot.Utils;

import javax.mail.*;
import javax.mail.internet.MimeBodyPart;
import java.io.IOException;
import java.util.Date;
import java.util.Properties;
import java.util.Random;

/**
 * Created by Maksim on 12.02.2017.
 */
public class MailService {
    private String host = "imap.yandex.ru";
    private String username;
    private String password;
    private static final String PROVIDER = "imap";
    private Long ownerChatId;
    private Properties props;
    private Date lastMessageDate;

    private MessageListener bot;
    private Thread mailUpdateThread;

    private Store store;
    private Folder inbox;

    private int messagesCount = -1;

    public MailService() {
        /*this.host = host;
        this.username = username;
        this.password = password;*/
        props = new Properties();
        props.setProperty("mail.imap.starttls.enable", "true");
        props.put("mail.imap.auth", "true");
        props.put("mail.imap.socketFactory.port", "993");
        props.put("mail.imap.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        props.put("mail.imap.socketFactory.fallback", "false");
    }

    public void connect() throws MessagingException {
        Utils.checkParam(host, "хост");
        Utils.checkParam(username, "email");
        Utils.checkParam(password, "пароль");

        Session session = Session.getDefaultInstance(props, null);
        store = session.getStore(PROVIDER);
        store.connect(host, username, password);
        if (store.isConnected()){
            startMailUpdate();
        }
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
        mailUpdateThread.setName("Updater thread " + new Random().nextInt(1000));
        mailUpdateThread.start();
    }


    private void performNewMails(Folder inbox, int oldMessagesCount, int newMessageCount) throws MessagingException, IOException {
        Message[] messages = inbox.getMessages(oldMessagesCount + 1, newMessageCount);
        for(Message message : messages) {
            parseMessage(message);
        }
    }


    private void performNewMailsWithDeleted(Folder inbox, int newMessageCount) throws MessagingException, IOException {
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
        String contentType = message.getContentType();
        if (contentType.contains("multipart")) {
            Multipart multiPart = (Multipart) message.getContent();
            int numberOfParts = multiPart.getCount();
            for (int partCount = 0; partCount < numberOfParts; partCount++) {
                MimeBodyPart part = (MimeBodyPart) multiPart.getBodyPart(partCount);
                if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                        && Utils.isImage(part.getFileName())) {
                    bot.sendToTelegram(part.getFileName(), part.getInputStream(), ownerChatId);
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
                System.out.println("1st count:" + messagesCount);
                while (true) {
                    System.out.println(Thread.currentThread().getName() + " --- " + i);
                    i++;

                    reopenFolder();
                    int newMessageCount = inbox.getMessageCount();
                    if (newMessageCount > messagesCount) {
                        performNewMails(inbox, messagesCount, newMessageCount);
                    }
                    if (newMessageCount < messagesCount) {
                        performNewMailsWithDeleted(inbox, newMessageCount);
                    }
                    messagesCount = newMessageCount;
                    System.out.println(newMessageCount);
                    inbox.close(false);
                    inbox = store.getFolder("INBOX");
                    inbox.open(Folder.READ_ONLY);
                    Thread.sleep(2000L);
                }

            } catch (MessagingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }

    public void stop(){
        if(mailUpdateThread != null && mailUpdateThread.isAlive())
            mailUpdateThread.stop();

        try {
            if (inbox != null && store != null && inbox.isOpen() && store.isConnected()) {
                inbox.close(false);
                store.close();
            }
        } catch (MessagingException e) {
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
