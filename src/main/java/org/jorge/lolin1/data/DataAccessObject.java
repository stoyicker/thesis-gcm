package org.jorge.lolin1.data;


import lol4j.util.Region;
import org.jorge.lolin1.control.ListManager;
import org.jorge.lolin1.models.champion.Champion;
import org.jorge.lolin1.models.champion.spells.ActiveSpellFactory;
import org.jorge.lolin1.utils.LoLin1DataProviderUtils;

import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.*;

/**
 * This file is part of lolin1-data-provider.
 * <p/>
 * lolin1-data-provider is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * lolin1-data-provider is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with lolin1-data-provider.  If not, see <http://www.gnu.org/licenses/>.
 */
public abstract class DataAccessObject {

    private final static String RESPONSE_UNSUPPORTED = "{\"status\":\"unsupported\"}";
    private static Map<Region, String> CHAMPIONS_VERSION_MAP = new HashMap<>(),
            CDN_MAP = new HashMap<>();
    private static final Map<Region, String[]> SUPPORTED_REALMS = new HashMap<>();

    public final static String CHAMPIONS_DIR_NAME = "champs";
    private static final Map<String, Charset> charsetMap = new HashMap<>();

    public static void initCharsetMap() {
        charsetMap.put("tr_TR", Charset.forName("UTF-8"));
        charsetMap.put("en_US", Charset.forName("UTF-8"));
        charsetMap.put("es_ES", Charset.forName("UTF-8"));
        charsetMap.put("de_DE", Charset.forName("UTF-8"));
        charsetMap.put("fr_FR", Charset.forName("UTF-8"));
        charsetMap.put("it_IT", Charset.forName("UTF-8"));
        charsetMap.put("pt_PT", Charset.forName("UTF-8"));
        charsetMap.put("el_GR", Charset.forName("UTF-8"));
        charsetMap.put("pl_PL", Charset.forName("UTF-8"));
        charsetMap.put("ro_RO", Charset.forName("UTF-8"));
    }

    public static String formatChampionListAsJSON(List<Champion> champions) {
        StringBuilder ret = new StringBuilder("[");
        for (Iterator<Champion> it = champions.iterator(); it.hasNext(); ) {
            ret.append(it.next().toString());
            if (it.hasNext()) {
                ret.append(",");
            }
        }
        return ret.append("]").toString();
    }

    public static String getChampionsDirName() {
        return DataAccessObject.CHAMPIONS_DIR_NAME;
    }

    public static String getJSONCDN(String realm) {
        StringBuffer ret;
        if (!DataAccessObject.CHAMPIONS_VERSION_MAP.containsKey(LoLin1DataProviderUtils.regionalizeString(realm))) {
            ret = new StringBuffer(DataAccessObject.getResponseUnsupported());
        } else {
            ret = new StringBuffer("{\"status\":\"ok\", \"version\":\""
                    + DataAccessObject.CDN_MAP.get(LoLin1DataProviderUtils.regionalizeString(realm)) + "\"}");
        }
        return ret.toString();
    }

    public static String getJSONList(String realm, String locale) {
        StringBuffer ret;
        try {
            if (Arrays.asList(DataAccessObject.getSupportedRealms().get(LoLin1DataProviderUtils.regionalizeString(realm)))
                    .contains(locale)) {
                String champions = LoLin1DataProviderUtils.readFile(Paths.get(
                        DataAccessObject.CHAMPIONS_DIR_NAME,
                        ListManager.getListsDirName(), realm, locale,
                        ListManager.getChampionsListFileName()));
                ret = new StringBuffer(champions);
            } else {
                ret = new StringBuffer(
                        DataAccessObject.getResponseUnsupported());
            }
        } catch (NullPointerException ex) {
            ret = new StringBuffer(DataAccessObject.getResponseUnsupported());
        }

        return ret.toString();
    }

    public static String getJSONVersion(String realm) {
        StringBuffer ret;
        if (!DataAccessObject.CHAMPIONS_VERSION_MAP.containsKey(LoLin1DataProviderUtils.regionalizeString(realm))) {
            ret = new StringBuffer(DataAccessObject.getResponseUnsupported());
        } else {
            ret = new StringBuffer("{\"status\":\"ok\", \"version\":\""
                    + DataAccessObject.CHAMPIONS_VERSION_MAP.get(LoLin1DataProviderUtils.regionalizeString(realm)) + "\"}");
        }
        return ret.toString();
    }

    public static String getResponseUnsupported() {
        return DataAccessObject.RESPONSE_UNSUPPORTED;
    }

    public static Map<Region, String[]> getSupportedRealms() {
        return DataAccessObject.SUPPORTED_REALMS;
    }

    public static String getVersion(Region realm) {
        return DataAccessObject.CHAMPIONS_VERSION_MAP.get(realm);
    }

    public static void initStaticData() {
        initRealms();
        initCharsetMap();
        ActiveSpellFactory.initLocalizationMaps();
    }

    public static Charset getLocaleCharset(String locale) {
        return charsetMap.containsKey(locale) ? charsetMap.get(locale) :
                Charset.forName(locale);
    }

    private static void initRealms() {
        DataAccessObject.SUPPORTED_REALMS.put(Region.EUW, new String[]{"en_US",
                "de_DE", "es_ES", "fr_FR", "it_IT"});
        DataAccessObject.SUPPORTED_REALMS.put(Region.EUNE, new String[]{"en_US",
                "el_GR", "pl_PL", "ro_RO"});
        DataAccessObject.SUPPORTED_REALMS.put(Region.NA, new String[]{"en_US"});
        DataAccessObject.SUPPORTED_REALMS.put(Region.TR, new String[]{"tr_TR"});
        DataAccessObject.SUPPORTED_REALMS.put(Region.BR, new String[]{"pt_PT"});

        for (Region realm : DataAccessObject.getSupportedRealms().keySet()) {
            DataAccessObject.CHAMPIONS_VERSION_MAP.put(realm, "");
            DataAccessObject.CDN_MAP.put(realm, "");
        }
    }

    public static void putCDN(Region realm, String url) {
        DataAccessObject.CDN_MAP.put(realm, url);
    }

    protected static void setChampionsVersion(Region realm,
                                              String newVersion) {
        DataAccessObject.CHAMPIONS_VERSION_MAP.put(realm, newVersion);
    }
}
