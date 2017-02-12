package tk.dzrcc.mail2bot.mail;

import java.io.InputStream;

/**
 * Created by Maksim on 12.02.2017.
 */
public interface MessageListener {
    void performMessage(String name, InputStream image, Long chatId);
}
