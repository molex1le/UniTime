# Следующая пара

Android-приложение с расписанием БГУИР: следующая пара в уведомлении и на экране блокировки, виджет, лента до сессии, дедлайны, общие окна с друзьями.

Данные — из открытого API `iis.bsuir.by`. Неофициальное приложение, не связано с БГУИР.

## Сборка

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Нужны JDK 17+ и Android SDK (путь в `local.properties`, `sdk.dir=...`).
