/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.logging.encoder;

import ch.qos.logback.classic.pattern.Abbreviator;
import ch.qos.logback.classic.pattern.ClassNameOnlyAbbreviator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.LayoutBase;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Log layout for AndroidIDE.
 *
 * @author Akash Yadav
 */
public class IDELogFormatLayout extends LayoutBase<ILoggingEvent> {

  private final Abbreviator loggerNameAbbreviator = new ClassNameOnlyAbbreviator();
  private boolean omitMessage = false;

  public void setOmitMessage(boolean omitMessage) {
    this.omitMessage = omitMessage;
  }

  public boolean isOmitMessage() {
    return omitMessage;
  }

  @Override
  public String doLayout(ILoggingEvent event) {
    final SimpleDateFormat format = new SimpleDateFormat("dd-MM HH:mm:ss.SSS", Locale.ROOT);
    final var date = format.format(new Date(event.getTimeStamp()));

    final var builder = new StringBuilder();
    builder.append(date);
    builder.append(' ');
    builder.append(String.format(Locale.ROOT, "%5s", event.getLevel().levelStr));
    builder.append(' ');
    builder.append("[");
    builder.append(event.getThreadName());
    builder.append("]");
    builder.append(' ');
    builder.append(safeAbbreviate(event.getLoggerName()));
    builder.append(": ");

    if (!isOmitMessage()) {
      builder.append(safeGetFormattedMessage(event));
      builder.append(System.lineSeparator());
    }

    return builder.toString();
  }

  /**
   * Safely abbreviate the logger name. If abbreviation fails for any reason, fall back to the
   * raw logger name. This prevents a {@link Throwable} from the abbreviator from killing the
   * layout.
   */
  private String safeAbbreviate(String loggerName) {
    if (loggerName == null) {
      return "";
    }
    try {
      return loggerNameAbbreviator.abbreviate(loggerName);
    } catch (Throwable t) {
      // Logback layout must never throw; otherwise the LogcatAppender.append() invocation will
      // see the exception and the logging pipeline will be left in a broken state.
      return loggerName;
    }
  }

  /**
   * Safely retrieve the formatted message. If {@link ILoggingEvent#getFormattedMessage()} throws
   * (e.g. {@code NoClassDefFoundError: org.slf4j.helpers.FormattingTuple} when SLF4J's
   * MessageFormatter is missing due to R8 stripping), fall back to the raw message text. This
   * is the root-cause fix for the recurring
   * "Fatal Exception: java.lang.NoClassDefFoundError: org.slf4j.helpers.FormattingTuple" crash
   * observed at {@code IDELogFormatLayout.doLayout}.
   */
  private String safeGetFormattedMessage(ILoggingEvent event) {
    try {
      final String formatted = event.getFormattedMessage();
      // Some logback versions can return null for certain events.
      return formatted != null ? formatted : "";
    } catch (NoClassDefFoundError e) {
      // R8 has stripped one of SLF4J's helper classes used during parameter substitution.
      // Don't let this kill the entire logging pipeline; fall back to the raw template.
      return safeGetMessage(event, e);
    } catch (Throwable t) {
      return safeGetMessage(event, t);
    }
  }

  private String safeGetMessage(ILoggingEvent event, Throwable cause) {
    try {
      final String raw = event.getMessage();
      return raw != null ? raw : "";
    } catch (Throwable ignored) {
      // As a last resort, return a string that is still safe to log.
      return "<unavailable message: " + cause.getClass().getName() + ">";
    }
  }
}
