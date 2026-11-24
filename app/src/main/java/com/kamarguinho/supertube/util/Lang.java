package com.kamarguinho.supertube.util;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Idioma do app: português (padrão) ou inglês. Os textos fixos ficam em res/values e
 * res/values-en; aqui ficam os textos montados no código (categorias, gêneros, avisos).
 */
public final class Lang {

    public static final String SYSTEM = "";
    public static final String PT = "pt";
    public static final String EN = "en";

    private static volatile boolean english = false;
    private static final Map<String, String> EN_TEXT = new HashMap<>();
    /** Buscas em inglês para as categorias (quando o app está em inglês). */
    private static final Map<String, String> EN_QUERY = new HashMap<>();

    private Lang() {
    }

    /** Atualiza o idioma a partir da tela atual (chamado em cada tela que abre). */
    public static void update(final Context context) {
        final Locale l = context.getResources().getConfiguration().getLocales().get(0);
        final boolean en = l != null && "en".equals(l.getLanguage());
        if (en != english || !configured) {
            english = en;
            configured = true;
            final String country = l != null && !l.getCountry().isEmpty() ? l.getCountry()
                    : (en ? "US" : "BR");
            // Textos do YouTube ("há 2 dias", "2 days ago") no idioma do app
            NewPipe.setupLocalization(en ? new Localization("en", country)
                    : new Localization("pt", "BR"), new ContentCountry(country));
        }
    }

    private static boolean configured = false;

    public static boolean english() {
        return english;
    }

    public static Locale locale() {
        return english ? Locale.ENGLISH : Locale.forLanguageTag("pt-BR");
    }

    /** Escolha salva ("", "pt" ou "en"). */
    public static String chosen() {
        final LocaleListCompat list = AppCompatDelegate.getApplicationLocales();
        return list.isEmpty() || list.get(0) == null ? SYSTEM : list.get(0).getLanguage();
    }

    /** Troca o idioma do app (as telas se recriam sozinhas). */
    public static void choose(final String lang) {
        AppCompatDelegate.setApplicationLocales(SYSTEM.equals(lang)
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(lang));
    }

    /** Traduz um texto montado no código (se o app estiver em inglês). */
    public static String t(final String pt) {
        if (!english || pt == null) {
            return pt;
        }
        final String en = EN_TEXT.get(pt);
        return en != null ? en : pt;
    }

    /** Busca equivalente em inglês para uma categoria (se o app estiver em inglês). */
    public static String query(final String ptQuery) {
        if (!english || ptQuery == null) {
            return ptQuery;
        }
        final String en = EN_QUERY.get(ptQuery);
        return en != null ? en : ptQuery;
    }

    /** "1,2 mi" / "1.2M" */
    public static String count(final long n) {
        if (english) {
            if (n >= 1_000_000_000L) {
                return compact(n / 1e9) + "B";
            } else if (n >= 1_000_000L) {
                return compact(n / 1e6) + "M";
            } else if (n >= 1_000L) {
                return compact(n / 1e3) + "K";
            }
            return String.valueOf(n);
        }
        if (n >= 1_000_000_000L) {
            return compact(n / 1e9) + " bi";
        } else if (n >= 1_000_000L) {
            return compact(n / 1e6) + " mi";
        } else if (n >= 1_000L) {
            return compact(n / 1e3) + " mil";
        }
        return String.valueOf(n);
    }

    private static String compact(final double v) {
        if (v >= 100 || Math.abs(v - Math.round(v)) < 0.05) {
            return String.valueOf(Math.round(v));
        }
        return String.format(locale(), "%.1f", v);
    }

    private static void put(final String pt, final String en) {
        EN_TEXT.put(pt, en);
    }

    static {
        // Categorias
        put("Em alta", "Trending");
        put("Tudo", "All");
        put("Música", "Music");
        put("Jogos", "Gaming");
        put("Notícias", "News");
        put("Ao vivo", "Live");
        put("Podcasts", "Podcasts");
        put("Filmes", "Movies");
        put("Séries", "Series");
        put("Esportes", "Sports");
        put("Humor", "Comedy");
        put("Aprender", "Learning");
        put("Tecnologia", "Tech");
        put("Culinária", "Cooking");
        put("Infantil", "Kids");
        put("Filmes completos", "Full movies");
        put("Filmes e trailers", "Movies and trailers");
        // Gêneros de filmes
        put("Ação", "Action");
        put("Comédia", "Comedy");
        put("Terror", "Horror");
        put("Suspense", "Thriller");
        put("Drama", "Drama");
        put("Aventura", "Adventure");
        put("Ficção científica", "Sci-fi");
        put("Romance", "Romance");
        put("Infantil e animação", "Kids and animation");
        put("Nacional", "Brazilian");
        put("Policial", "Crime");
        put("Guerra", "War");
        put("Faroeste", "Western");
        put("Fé e família", "Faith and family");
        put("Clássicos", "Classics");
        put("Documentários", "Documentaries");
        put("Em inglês", "In English");
        // Séries
        put("Séries dubladas", "Dubbed series");
        put("Ação e aventura", "Action and adventure");
        put("Suspense e mistério", "Thriller and mystery");
        put("Animes", "Anime");
        put("Desenhos", "Cartoons");
        put("Novelas", "Soap operas");
        put("Doramas", "K-dramas");
        put("Séries nacionais", "Brazilian series");
        // Música
        put("Mais tocadas", "Top hits");
        put("Sertanejo", "Sertanejo");
        put("Funk", "Funk");
        put("Pagode e samba", "Pagode and samba");
        put("Forró e piseiro", "Forró");
        put("Gospel", "Gospel");
        put("MPB", "MPB (Brazilian pop)");
        put("Pop", "Pop");
        put("Rock", "Rock");
        put("Eletrônica", "Electronic");
        put("Rap e hip-hop", "Rap and hip-hop");
        put("Românticas", "Love songs");
        put("Anos 80 e 90", "80s and 90s");
        put("Reggae", "Reggae");
        put("Para relaxar", "Chill");
        // Abas do canal
        put("Vídeos", "Videos");
        put("Shorts", "Shorts");
        put("Playlists", "Playlists");
        put("Álbuns", "Albums");
        // Áudio e legendas
        put("original", "original");
        put("dublado", "dubbed");
        put("descrição de áudio", "audio description");
        put("gerada automaticamente", "auto-generated");
        put("Português (tradução automática)", "Portuguese (auto-translated)");
        put("Inglês (tradução automática)", "English (auto-translated)");
        put("Original", "Original");
        put("Vídeo", "Video");
        put("Áudio", "Audio");
        // Avisos
        put("Carregando título…", "Loading title…");
        put("Vídeo indisponível", "Video unavailable");
        put("Não foi possível abrir o arquivo", "Could not open the file");
        put("Não conectado", "Not signed in");
        put("Não foi possível criar o código. Tente de novo.",
                "Could not create the code. Try again.");
        put("Código não encontrado. Confira o número que aparece na TV.",
                "Code not found. Check the number shown on the TV.");
        put("Este e-mail já tem conta. Use \"Entrar\".",
                "This email already has an account. Use \"Sign in\".");
        put("E-mail ou senha incorretos.", "Wrong email or password.");
        put("A senha precisa ter pelo menos 6 caracteres.",
                "The password needs at least 6 characters.");
        put("E-mail inválido.", "Invalid email.");
        put("Muitas tentativas. Espere um pouco e tente de novo.",
                "Too many attempts. Wait a bit and try again.");
        put("Login não ativado no Firebase (ative E-mail/senha e Anônimo).",
                "Sign-in not enabled in Firebase (enable Email/password and Anonymous).");
        put("Página de versões não encontrada. O repositório do GitHub precisa ser público.",
                "Releases page not found. The GitHub repository must be public.");
        put("Nenhum formato de vídeo compatível foi encontrado",
                "No compatible video format was found");
        put("Transmissão ao vivo sem link compatível", "Live stream without a compatible link");
        put("vídeos…", "videos…");

        // Buscas das categorias simples
        EN_QUERY.put("games gameplay", "gaming gameplay");
        EN_QUERY.put("notícias hoje", "news today");
        EN_QUERY.put("ao vivo agora", "live now");
        EN_QUERY.put("podcast", "podcast");
        EN_QUERY.put("esportes melhores momentos", "sports highlights");
        EN_QUERY.put("humor comédia", "comedy sketches");
        EN_QUERY.put("como fazer tutorial", "how to tutorial");
        EN_QUERY.put("tecnologia review", "tech review");
        EN_QUERY.put("receita fácil", "easy recipe");
        EN_QUERY.put("música", "music");
        EN_QUERY.put("trailer filme", "movie trailer");
        EN_QUERY.put("desenho infantil", "kids cartoons");
        EN_QUERY.put("em alta hoje", "trending today");
    }
}
