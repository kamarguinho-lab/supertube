# SuperTube

App Android para assistir vídeos do YouTube **sem anúncios e sem conta**, no estilo do NewPipe.

## Funções

- **Busca e player**: pesquise e assista em até 1080p.
- **Segundo plano**: o vídeo continua tocando com a tela desligada ou em outro app, com controles na notificação. O botão **Só áudio** economiza dados.
- **Download**: baixe vídeos (com som) ou só o áudio. Os arquivos vão para `Downloads/SuperTube`.
- **Inscrições locais**: siga canais sem conta do Google. Tudo fica salvo só no celular.
- **Abrir links**: compartilhe um link do YouTube com o SuperTube, ou abra o link direto nele.

Dica: toque e segure um vídeo da lista para abrir o canal.

## Como instalar

1. Abra a página **Releases** deste repositório no celular.
2. Baixe o arquivo `SuperTube.apk` da versão mais recente.
3. Abra o arquivo e permita "instalar apps desconhecidos" quando o Android pedir.

Cada vez que o código muda na branch `main`, o GitHub Actions gera um APK novo sozinho.

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
