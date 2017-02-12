package tk.dzrcc.mail2bot.mail;

import tk.dzrcc.mail2bot.Utils;

import javax.mail.*;
import javax.mail.event.MessageCountEvent;
import javax.mail.event.MessageCountListener;
import javax.mail.internet.MimeBodyPart;
import java.io.IOException;
import java.util.Properties;

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

    private MessageListener bot;

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
            openFolder();
        }
    }

    private void openFolder() throws MessagingException {
        inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);
        messagesCount = inbox.getMessageCount();
        System.out.println("1st count:"+messagesCount);
        Thread mailUpdate = new Thread(new MailUpdate());
        inbox.close(false);
        inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);
        mailUpdate.start();
        /*inbox.addMessageCountListener(new MessageCountListener() {
            public void messagesAdded(MessageCountEvent messageCountEvent) {
                try {
                    Message[] messages = messageCountEvent.getMessages();
                    for (Message message : messages) {
                        String contentType = message.getContentType();
                        if (contentType.contains("multipart")) {
                            Multipart multiPart = (Multipart) message.getContent();
                            int numberOfParts = multiPart.getCount();
                            for (int partCount = 0; partCount < numberOfParts; partCount++) {
                                MimeBodyPart part = (MimeBodyPart) multiPart.getBodyPart(partCount);
                                if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                                        && Utils.isImage(part.getFileName())) {
                                    bot.performMessage(part.getFileName(), part.getInputStream(), ownerChatId);
                                }
                            }
                        }
                    }
                } catch (MessagingException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            public void messagesRemoved(MessageCountEvent messageCountEvent) {

            }
        });*/
    }

    class MailUpdate implements Runnable {
        public void run() {
            while (true){
                System.out.println(".");
                try {


                    Thread.sleep(5000L);
                    int a = inbox.getMessageCount();
                    int b = inbox.getDeletedMessageCount();
                    System.out.println(a);
                    System.out.println(b);
                    inbox.close(false);
                    inbox = store.getFolder("INBOX");
                    inbox.open(Folder.READ_ONLY);
                   /* if (a>0) {

                        System.out.println(inbox.getMessage(inbox.getMessageCount()-1).getSubject());
                        inbox.close(false);
                        inbox = store.getFolder("INBOX");
                        inbox.open(Folder.READ_ONLY);
                    }*/
                        //System.out.println("New");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (MessagingException e) {
                    e.printStackTrace();
                }
            }
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
