# CueLightShow for Android

This framework contains SDK for CUE Live Lightshow 2.0.

## Usage
1.Add the JitPack repository to your **settings.gradle** file
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```
2.Add the dependency to **build.gradle (Module:app)**. Set up actual android-webview-sdk [version](https://github.com/Transported-Labs/android-webview-sdk/tags) 
```kotlin
dependencies {
    implementation 'com.github.Transported-Labs:android-webview-sdk:0.0.6'
}
```
## Integration

Simply execute the following code:

```kotlin
        navigateButton.setOnClickListener {
            val url = "<your URL from CUE>"
            try {
                webViewController.navigateTo(url)
            } catch (e: InvalidUrlError) {
                // Show invalid URL error message
                Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
            }
        }
```
## Pre-fetch

To pre-fetch lightshow resources is very similar to navigation.
Just execute the following code:

```kotlin
        prefetchButton.setOnClickListener {
            val url = "<your URL from CUE>"
            try {
                webViewController.prefetch(url) { logLine ->
                    // You can get progress from the pre-fetch process
                    // Add logLine string to your progress log
                    logText.text.appendLine(logLine)
                }
            } catch (e: InvalidUrlError) {
                // Show invalid URL error message
                Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
            }
        }
```

## Using PRIVACY flag

You can pass optional PRIVACY flag to prevent collecting and sending to the server any user information. SDK initialization in this case looks like that:

[insert code example] 

## CLIENT_URL_STRING

We do not recommend hard-coding a URL string, as it varies by client. The branding is controlled dynamically. In order to pre-fetch this branding so it shows as soon as the CUE SDK is opened, please execute this code once your app launches:

[INSERT INSTRUCTIONS ON HOW TO DO THIS, SIMILAR TO THIS FROM 1.0: https://github.com/CUEAudio/sdk_demo_ios?tab=readme-ov-file#api-key]

## HOW TO TEST

In order to test, you can play an audio file to trigger a light show with the CUE SDK open. The audio file is specific to the CLIENT_URL_STRING. In order to get the right audio file for your CLIENT_URL_STRING, simply:

[insert instructions on how someone can download the demo audio file based on your client URL string]


