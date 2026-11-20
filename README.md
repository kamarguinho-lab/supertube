# SuperTube

App Android para assistir vídeos do YouTube **sem anúncios e sem conta**, no estilo do NewPipe.

## Funções

- **Busca e player**: pesquise e assista em até 1080p.
- **Segundo plano**: o vídeo continua tocando com a tela desligada ou em outro app, com controles na notificação. O botão **Só áudio** economiza dados.
- **Download**: baixe vídeos (com som) ou só o áudio. Os arquivos vão para `Downloads/SuperTube`.
- **Inscrições locais**: siga canais sem conta do Google. Tudo fica salvo só no celular.
- **Favoritos**: guarde vídeos com o botão **Favoritar**.
- **Importar do YouTube** (aba Conta): traga suas inscrições e vídeos curtidos pelo **login Google** ou por um arquivo do **Google Takeout**.
- **Abrir links**: compartilhe um link do YouTube com o SuperTube, ou abra o link direto nele.

Dica: toque e segure um vídeo da lista para abrir o canal.

## Como instalar

1. Abra a página **Releases** deste repositório no celular.
2. Baixe o arquivo `SuperTube.apk` da versão mais recente.
3. Abra o arquivo e permita "instalar apps desconhecidos" quando o Android pedir.

Cada vez que o código muda na branch `main`, o GitHub Actions gera um APK novo sozinho.

## Ativar o login Google (uma vez só)

O login usa a YouTube Data API. Ele só funciona depois desta configuração no Google Cloud:

1. Entre em https://console.cloud.google.com e crie um projeto (ex.: `SuperTube`).
2. Em **APIs e serviços → Biblioteca**, ative a **YouTube Data API v3**.
3. Em **Tela de permissão OAuth**: tipo **Externo**, preencha nome e e-mail, adicione o escopo
   `.../auth/youtube.readonly` e, em **Usuários de teste**, cadastre os e-mails Google
   de quem vai usar (até 100 pessoas).
4. Em **Credenciais → Criar credenciais → ID do cliente OAuth**, escolha **Android**:
   - Nome do pacote: `com.kamarguinho.supertube`
   - SHA-1: `23:C0:57:1E:67:81:D4:0A:62:F8:5F:BA:A7:72:45:E3:BE:0A:C0:29`
5. Pronto. Não precisa mudar nada no código.

Para liberar o login para qualquer pessoa (sem lista de testadores), o Google precisa aprovar
o app. Como o app tira anúncios, essa aprovação pode ser recusada. A importação pelo
Google Takeout funciona para todo mundo sem nada disso.

## Como compilar no computador

Abra a pasta no Android Studio e clique em **Run**, ou rode:

```
./gradlew assembleDebug
```

O APK sai em `app/build/outputs/apk/debug/`.

## Tecnologia

- Java, Android 7.0 ou mais novo
- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) para ler os vídeos
- ExoPlayer para tocar
- Partes do código vêm do [NewPipe](https://github.com/TeamNewPipe/NewPipe)

## Licença

GPLv3, a mesma do NewPipe (veja `LICENSE`). O código precisa continuar aberto.

Usar um app como este vai contra os termos de uso do YouTube.
