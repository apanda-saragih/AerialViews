# Development Setup Guide for Aerial Views

This guide will help you set up the development environment for the Aerial Views Android app.

## Required Files Created

The following configuration files have been created for you:

### 1. `secrets.properties`
Contains OpenWeather API keys for weather functionality.

### 2. `signing/release.properties`
Configuration for release build signing.

### 3. `signing/legacy.properties`
Configuration for legacy/beta build signing.

## Next Steps

### Step 1: Get OpenWeather API Key

1. Go to [OpenWeatherMap API](https://openweathermap.org/api)
2. Sign up for a free account
3. Get your API key from the dashboard
4. Replace `your_api_key_here` in `secrets.properties` with your actual API key

**Example:**
```properties
openWeather=abc123def456ghi789
openWeatherDebug=abc123def456ghi789
```

### Step 2: Create Keystore Files (Optional for Debug)

For debug builds, you don't need keystores. However, if you want to build release versions:

#### Create Release Keystore:
```bash
keytool -genkey -v -keystore your-release-key.keystore -alias your-key-alias -keyalg RSA -keysize 2048 -validity 10000
```

#### Create Legacy Keystore:
```bash
keytool -genkey -v -keystore your-legacy-key.keystore -alias your-legacy-alias -keyalg RSA -keysize 2048 -validity 10000
```

### Step 3: Update Configuration Files

1. **Update `secrets.properties`** with your OpenWeather API key
2. **Update `signing/release.properties`** with your release keystore details
3. **Update `signing/legacy.properties`** with your legacy keystore details

### Step 4: Build the Project

#### For Debug Build (Recommended for development):
```bash
./gradlew assembleBetaDebug
```

#### For Release Build:
```bash
./gradlew assembleBetaRelease
```

## Build Variants

- **beta** (default) - Beta version with debug features
- **github** - GitHub release version  
- **googleplay** - Google Play Store version
- **amazon** - Amazon Appstore version
- **fdroid** - F-Droid version (no Firebase)

## Running the App

### Option 1: Android Studio
1. Open the project in Android Studio
2. Connect an Android device or start an emulator
3. Click "Run" button

### Option 2: Command Line
```bash
# Install debug version
./gradlew installBetaDebug

# Install release version
./gradlew installBetaRelease
```

## Important Notes

- The app requires Android API 22+ (Android 5.1+)
- For screensaver functionality, you may need to set it as the default screensaver using ADB commands (see README.md)
- Weather features require a valid OpenWeather API key
- Some features may require specific device permissions

## Troubleshooting

- If build fails, check that all required files are properly configured
- Make sure you have the correct Android SDK and build tools installed
- Check that your API keys are valid and have the correct permissions
