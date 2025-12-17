package io.github.fichil.packager.core.config;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VarResolver {
    private static final Pattern P = Pattern.compile("\\$\\{([A-Za-z0-9_\\-\\.]+)\\}");

    private VarResolver() {}

    public static String resolve(String text, Map<String, String> vars) {
        if (text == null || text.isEmpty() || vars == null || vars.isEmpty()) return text;

        String result = text;
        // 最多迭代几轮，避免 vars 里互相引用造成死循环
        for (int i = 0; i < 5; i++) {
            Matcher m = P.matcher(result);
            StringBuffer sb = new StringBuffer();
            boolean changed = false;
            while (m.find()) {
                String key = m.group(1);
                String val = vars.get(key);
                if (val == null) val = m.group(0); // 找不到就原样保留，方便排错
                else changed = true;
                m.appendReplacement(sb, Matcher.quoteReplacement(val));
            }
            m.appendTail(sb);
            result = sb.toString();
            if (!changed) break;
        }
        return result;
    }
}
