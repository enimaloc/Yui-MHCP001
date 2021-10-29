package fr.enimaloc.yui;

import java.io.InputStream;
import java.util.*;

public class Constant {
    public static final Version VERSION = new Version(0, 0, 0, new String[]{"beta"}, new String[]{"draft"});
    
    public static final String PREFIX = "!";
    
    public static final String EMOJI_SUCCESS = "";
    public static final String EMOJI_WARNING = "";
    public static final String EMOJI_ERROR = "";

    public static final String GIT_BRANCH;
    
    public static final long[] OWNERS_ID = {
            136200628509605888L, // enimaloc(Discord: https://discord.com/users/136200628509605888 Github: https://github.com/enimaloc/)
            403594452712816641L  // Kévinou la banane 🍌(Discord: https://discord.com/users/403594452712816641 Github: https://github.com/KevinouLaBanane)
    };

    static {
        InputStream is = Constant.class.getClassLoader()
                                       .getResourceAsStream("constant.txt");
        Scanner scanner = new Scanner(Objects.requireNonNull(is));
        Map<String, String> values = new HashMap<>();
        while (scanner.hasNextLine()) {
            String[] split = scanner.nextLine().split("=", 2);
            values.put(split[0].toUpperCase(Locale.ROOT), split[1]);
        }
        GIT_BRANCH = values.get("GIT_BRANCH");
    }
}
