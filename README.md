# Revivo - Android Application

**Revivo** è un'applicazione Android moderna sviluppata in Kotlin e Jetpack Compose, con integrazione WebView avanzata, servizio in background per l'upload dei dati, gestione avanzata delle immagini e condivisione nativa tramite i principali social network.

---

## 🚀 Caratteristiche Principali

- **UI Moderna con Jetpack Compose**: Interfaccia utente fluida, reattiva e con supporto Edge-to-Edge.
- **WebView Avanzata & Native Bridge**: Integrazione bidirezionale tra JavaScript e Kotlin nativo (`RevivoNative`).
- **Servizio in Background per Upload**: `UploadForegroundService` per garantire caricamento e sincronizzazione affidabili.
- **Gestione Immagini & Condivisione Social**: Salvataggio nativo in galleria (MediaStore) e condivisione mirata verso **Instagram (Stories/Feed)**, **WhatsApp**, **Facebook** e **Snapchat**.
- **Gestione Connettività**: Rilevamento dello stato di rete e gestione delle schermate di fallback offline.
- **Gestione Sicura dei Secret**: Integrazione con Secrets Gradle Plugin tramite file `.env`.
- **Persistenza & Backend**: Configurato con **Room Database** e **Firebase**.

---

## 🛠️ Tecnologie e Librerie

- **Linguaggio**: Kotlin
- **UI Framework**: Jetpack Compose & Material Design 3
- **Concorrenza**: Kotlin Coroutines & Flow
- **Network & Parsing**: Retrofit, OkHttp, Moshi
- **Database Locale**: Room
- **Backend & Cloud**: Firebase App Check, Google Play Services
- **Testing**: JUnit 4, Robolectric, Roborazzi (Screenshot Testing)
- **Build System**: Gradle (Kotlin DSL `.gradle.kts`)

---

## 📋 Requisiti

- **Android Studio**: Android Studio Koala / Ladybug / 2024.1+ o più recente
- **JDK**: Java 11
- **Min SDK**: Android 7.0 (API Level 24)
- **Target SDK**: Android 16 (API Level 36)

---

## ⚙️ Configurazione del Progetto

1. **Clona il Repository**:
   ```bash
   git clone https://github.com/TUO-USERNAME/Revivo.git
   cd Revivo
   ```

2. **Configura le Variabili d'Ambiente (`.env`)**:
   Crea un file `.env` nella radice del progetto:
   ```env
   # Inserisci qui le tue variabili di configurazione
   ```

3. **Integrazione Firebase**:
   Se necessario per il tuo ambiente, posiziona il file `google-services.json` all'interno della cartella `app/`.

4. **Sincronizza e Compila**:
   - Apri il progetto in Android Studio.
   - Esegui **Sync Project with Gradle Files**.
   - Avvia l'applicazione su un emulatore o dispositivo fisico.

---

## 🔒 Sicurezza e .gitignore

I seguenti file sensibili sono esclusi dal tracciamento Git tramite `.gitignore`:
- `local.properties`
- File `.env`
- File di KeyStore (`*.jks`, `debug.keystore`)
- `google-services.json`

---

## 📄 Licenza

Questo progetto è distribuito sotto licenza MIT.
