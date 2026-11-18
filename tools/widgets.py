import os, sys
RES = sys.argv[1]
os.makedirs(f"{RES}/layout", exist_ok=True)
os.makedirs(f"{RES}/xml", exist_ok=True)

NS = 'xmlns:android="http://schemas.android.com/apk/res/android"\n    xmlns:tools="http://schemas.android.com/tools"'


def halves(n):
    return f'''<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">
    <ImageView
        android:id="@+id/w_card{n}_top"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:importantForAccessibility="no"
        android:scaleType="fitXY"
        android:src="@drawable/wcard_top"
        android:tint="@color/widget_default_card_top" />
    <ImageView
        android:id="@+id/w_card{n}_bottom"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:importantForAccessibility="no"
        android:scaleType="fitXY"
        android:src="@drawable/wcard_bottom"
        android:tint="@color/widget_default_card_bottom" />
</LinearLayout>'''


def seam(n):
    return f'''<ImageView
    android:id="@+id/w_card{n}_seam"
    android:layout_width="match_parent"
    android:layout_height="@dimen/widget_seam"
    android:layout_gravity="center_vertical"
    android:importantForAccessibility="no"
    android:scaleType="fitXY"
    android:src="@drawable/wseam"
    android:tint="@color/widget_default_screen" />'''


def textclock(id_, f12, f24, padding="@dimen/widget_digit_padding", maxsp=260):
    return f'''<TextClock
    android:id="@+id/{id_}"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:autoSizeMaxTextSize="{maxsp}sp"
    android:autoSizeMinTextSize="10sp"
    android:autoSizeStepGranularity="1sp"
    android:autoSizeTextType="uniform"
    android:fontFamily="sans-serif"
    android:format12Hour="{f12}"
    android:format24Hour="{f24}"
    android:gravity="center"
    android:includeFontPadding="false"
    android:maxLines="1"
    android:padding="{padding}"
    android:textColor="@color/widget_default_digit"
    android:textStyle="bold" />'''


AMPM = '''<TextClock
    android:id="@+id/w_ampm"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="top|start"
    android:layout_marginStart="@dimen/widget_ampm_margin"
    android:layout_marginTop="@dimen/widget_ampm_margin"
    android:format12Hour="a"
    android:format24Hour=""
    android:includeFontPadding="false"
    android:letterSpacing="0.08"
    android:textColor="@color/widget_default_accent"
    android:textSize="10sp"
    android:textStyle="bold" />'''

PLATE = '''<ImageView
    android:id="@+id/w_bg"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:importantForAccessibility="no"
    android:scaleType="fitXY"
    android:src="@drawable/widget_bg"
    android:tint="@color/widget_default_screen" />'''


def ind(s, n):
    pad = " " * n
    return "\n".join(pad + l if l.strip() else l for l in s.split("\n"))


def card(n, inner, layout_params):
    return f'''<FrameLayout
    android:id="@+id/w_card{n}"
{ind(layout_params, 4)}>
{ind(halves(n), 4)}
{ind(inner, 4)}
{ind(seam(n), 4)}
</FrameLayout>'''


WEIGHTED = '''android:layout_width="0dp"
android:layout_height="match_parent"
android:layout_weight="1"'''


def two_cards():
    c1 = card(1, textclock("w_hour", "h", "HH") + "\n" + AMPM, WEIGHTED)
    c2 = card(2, textclock("w_minute", "mm", "mm"), WEIGHTED + '\nandroid:layout_marginStart="@dimen/widget_card_gap"')
    return f'''<LinearLayout
    android:id="@+id/w_cards"
    android:layout_width="match_parent"
    android:layout_height="0dp"
    android:layout_weight="1"
    android:orientation="horizontal">
{ind(c1, 4)}
{ind(c2, 4)}
</LinearLayout>'''


def page(desc, body):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated: RemoteViews-safe views only (FrameLayout, LinearLayout, ImageView, TextClock, TextView). -->
<FrameLayout {NS}
    android:id="@+id/w_root_click"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:contentDescription="{desc}"
    android:padding="@dimen/widget_outer_padding">

    <FrameLayout
        android:id="@android:id/background"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:clipToOutline="true"
        tools:targetApi="31">
{ind(PLATE, 8)}
{ind(body, 8)}
    </FrameLayout>
</FrameLayout>
'''


INFO = '''<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="@dimen/widget_info_gap"
    android:gravity="center"
    android:orientation="horizontal">
    <TextClock
        android:id="@+id/w_date"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:ellipsize="end"
        android:format12Hour="EEE, d MMMM"
        android:format24Hour="EEE, d MMMM"
        android:includeFontPadding="false"
        android:letterSpacing="0.03"
        android:maxLines="1"
        android:shadowColor="#40000000"
        android:shadowDy="1"
        android:shadowRadius="2"
        android:textColor="@color/widget_default_secondary"
        android:textSize="14sp"
        android:textStyle="bold" />
    <LinearLayout
        android:id="@+id/w_alarm_group"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="12dp"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:visibility="gone">
        <ImageView
            android:id="@+id/w_alarm_icon"
            android:layout_width="14dp"
            android:layout_height="14dp"
            android:importantForAccessibility="no"
            android:src="@drawable/ic_widget_alarm"
            android:tint="@color/widget_default_accent" />
        <TextView
            android:id="@+id/w_alarm"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="4dp"
            android:includeFontPadding="false"
            android:maxLines="1"
            android:shadowColor="#40000000"
            android:shadowDy="1"
            android:shadowRadius="2"
            android:textColor="@color/widget_default_secondary"
            android:textSize="14sp"
            android:textStyle="bold" />
    </LinearLayout>
</LinearLayout>'''


def column(children, gravity=""):
    g = f'\n    android:gravity="{gravity}"' if gravity else ""
    return f'''<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent"{g}
    android:orientation="vertical"
    android:padding="@dimen/widget_inner_padding">
{ind(children, 4)}
</LinearLayout>'''


open(f"{RES}/layout/widget_clock.xml", "w").write(page("@string/widget_clock", column(two_cards())))
open(f"{RES}/layout/widget_clock_date.xml", "w").write(page("@string/widget_clock_date", column(two_cards() + "\n" + INFO)))

CAL_CARD = card(1, textclock("w_day", "d", "d", maxsp=300), '''android:layout_width="match_parent"
android:layout_height="0dp"
android:layout_weight="1"
android:layout_marginVertical="@dimen/widget_info_gap"''')
MONTH = '''<TextClock
    android:id="@+id/w_month"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:format12Hour="LLLL"
    android:format24Hour="LLLL"
    android:includeFontPadding="false"
    android:letterSpacing="0.12"
    android:maxLines="1"
    android:textAllCaps="true"
    android:textColor="@color/widget_default_accent"
    android:textSize="12sp"
    android:textStyle="bold" />'''
WEEKDAY = '''<TextClock
    android:id="@+id/w_weekday"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:format12Hour="EEEE"
    android:format24Hour="EEEE"
    android:includeFontPadding="false"
    android:maxLines="1"
    android:shadowColor="#40000000"
    android:shadowDy="1"
    android:shadowRadius="2"
    android:textColor="@color/widget_default_secondary"
    android:textSize="12sp"
    android:textStyle="bold" />'''
open(f"{RES}/layout/widget_calendar.xml", "w").write(
    page("@string/widget_calendar", column(MONTH + "\n" + CAL_CARD + "\n" + WEEKDAY, gravity="center_horizontal")))

MINI_CARD = card(1, textclock("w_time", "h:mm", "HH:mm", padding="@dimen/widget_mini_padding"), '''android:layout_width="match_parent"
android:layout_height="match_parent"''')
open(f"{RES}/layout/widget_mini.xml", "w").write(page("@string/widget_mini", column(MINI_CARD)))


def info_xml(layout, minW, minH, cw, ch, rminW, rminH, desc, preview):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:configure="dev.flip.clock.widget.WidgetConfigActivity"
    android:description="@string/{desc}"
    android:initialLayout="@layout/{layout}"
    android:minWidth="{minW}dp"
    android:minHeight="{minH}dp"
    android:minResizeWidth="{rminW}dp"
    android:minResizeHeight="{rminH}dp"
    android:previewImage="@drawable/{preview}"
    android:previewLayout="@layout/{layout}"
    android:resizeMode="horizontal|vertical"
    android:targetCellWidth="{cw}"
    android:targetCellHeight="{ch}"
    android:updatePeriodMillis="3600000"
    android:widgetCategory="home_screen|keyguard"
    android:widgetFeatures="reconfigurable|configuration_optional" />
'''


open(f"{RES}/xml/widget_clock_info.xml", "w").write(info_xml("widget_clock", 250, 110, 4, 2, 110, 50, "widget_clock_desc", "widget_preview_clock"))
open(f"{RES}/xml/widget_clock_date_info.xml", "w").write(info_xml("widget_clock_date", 250, 180, 4, 3, 180, 110, "widget_clock_date_desc", "widget_preview_clock_date"))
open(f"{RES}/xml/widget_calendar_info.xml", "w").write(info_xml("widget_calendar", 110, 110, 2, 2, 70, 90, "widget_calendar_desc", "widget_preview_calendar"))
open(f"{RES}/xml/widget_mini_info.xml", "w").write(info_xml("widget_mini", 180, 50, 3, 1, 110, 40, "widget_mini_desc", "widget_preview_mini"))
print("ok")
