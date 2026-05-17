package com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.src.app_package

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder

internal fun AndroidModuleTemplateBuilder.audioInterfaceKt() = """
package ${data.packageName}

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class AudioInterface(private val context: Context, private val initializationMessage: String) : DefaultLifecycleObserver {
  private lateinit var tts: TextToSpeech
  override fun onStart(owner: LifecycleOwner) {
    tts = TextToSpeech(context) { if (it == TextToSpeech.SUCCESS) tts.speak(initializationMessage, TextToSpeech.QUEUE_ADD, null, "init") }
  }
  override fun onStop(owner: LifecycleOwner) { tts.shutdown() }
}
""".trim()

internal fun AndroidModuleTemplateBuilder.audioInterfaceJava() = """
package ${data.packageName};

import android.content.Context;
import android.speech.tts.TextToSpeech;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

public class AudioInterface implements DefaultLifecycleObserver {
  private final Context context;
  private final String initializationMessage;
  private TextToSpeech tts;
  public AudioInterface(Context context, String initializationMessage) {
    this.context = context;
    this.initializationMessage = initializationMessage;
  }
  @Override public void onStart(LifecycleOwner owner) {
    tts = new TextToSpeech(context, status -> {
      if (status == TextToSpeech.SUCCESS) {
        tts.speak(initializationMessage, TextToSpeech.QUEUE_ADD, null, "init");
      }
    });
  }
  @Override public void onStop(LifecycleOwner owner) { if (tts != null) tts.shutdown(); }
}
""".trim()
