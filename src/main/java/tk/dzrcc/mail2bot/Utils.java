package tk.dzrcc.mail2bot;

import java.util.regex.Pattern;

/**
 * Created by Maksim on 12.02.2017.
 */
public class Utils {
    private static Pattern pattern = Pattern.compile(".(?:jpg|gif|png|jpeg|webp|svg)$");

    public static void checkParam(String param, String name){
        if (param == null || param.equals(""))
            throw new IllegalArgumentException("Необходимо указать " + name);
    }

    public static boolean isImage(String fileName){
        return pattern.matcher(fileName).find();
    }
}
