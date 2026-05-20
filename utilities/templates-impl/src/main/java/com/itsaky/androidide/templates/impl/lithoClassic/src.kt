package com.itsaky.androidide.templates.impl.lithoClassic

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder

internal fun AndroidModuleTemplateBuilder.appKt() =
    """
package ${data.packageName}

import android.app.Application
import com.facebook.soloader.SoLoader

class MyApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    SoLoader.init(this, false)
  }
}
"""

internal fun AndroidModuleTemplateBuilder.mainActivityKt() =
    """
package ${data.packageName}

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.facebook.litho.ComponentContext
import com.facebook.litho.LithoView
import com.facebook.litho.widget.Text

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val c = ComponentContext(this)
    val lithoView = LithoView.create(this, Text.create(c).text("Hello Litho").textSizeDip(32f).build())
    setContentView(lithoView)
  }
}
"""

internal fun AndroidModuleTemplateBuilder.appJava() =
    """
package ${data.packageName};

import android.app.Application;
import com.facebook.soloader.SoLoader;

public class MyApplication extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    SoLoader.init(this, false);
  }
}
"""

internal fun AndroidModuleTemplateBuilder.mainActivityJava() =
    """
package ${data.packageName};

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.facebook.litho.ComponentContext;
import com.facebook.litho.LithoView;
import com.facebook.litho.widget.Text;

public class MainActivity extends AppCompatActivity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ComponentContext c = new ComponentContext(this);
    LithoView lithoView = LithoView.create(this, Text.create(c).text("Hello Litho").textSizeDip(32).build());
    setContentView(lithoView);
  }
}
"""
