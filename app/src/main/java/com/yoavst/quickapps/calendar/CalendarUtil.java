package com.yoavst.quickapps.calendar;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract.Events;

import com.google.ical.compat.javautil.DateIterable;
import com.google.ical.compat.javautil.DateIteratorFactory;
import com.yoavst.quickapps.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.TimeZone;

import static android.provider.CalendarContract.Events.ALL_DAY;
import static android.provider.CalendarContract.Events.DISPLAY_COLOR;
import static android.provider.CalendarContract.Events.DTEND;
import static android.provider.CalendarContract.Events.DTSTART;
import static android.provider.CalendarContract.Events.DURATION;
import static android.provider.CalendarContract.Events.EVENT_LOCATION;
import static android.provider.CalendarContract.Events.RRULE;
import static android.provider.CalendarContract.Events.TITLE;
import static android.provider.CalendarContract.Events._ID;

/**
 * Created by Yoav.
 */
public class CalendarUtil {
	private CalendarUtil() {
	}

	private static final SimpleDateFormat dayFormatter = new SimpleDateFormat(
			"EEE, MMM d, yyyy");
	private static final SimpleDateFormat dateFormatter = new SimpleDateFormat(
			"EEE, MMM d, HH:mm");
	private static final SimpleDateFormat hourFormatter = new SimpleDateFormat("HH:mm");
	private static final SimpleDateFormat fullDateFormat = new SimpleDateFormat("EEE, MMM d");
	private static final SimpleDateFormat otherDayFormatter = new SimpleDateFormat("MMM d, HH:mm");

	public static ArrayList<Event> getCalendarEvents(Context context) {
		ArrayList<Event> events = new ArrayList<>();
		TimeZone timezone = Calendar.getInstance().getTimeZone();
		String selection = "((" + DTSTART + " >= ?) OR (" + DTEND + " >= ?))";
		String milli = String.valueOf(System.currentTimeMillis());
		String[] selectionArgs = new String[]{milli, milli};
		Cursor cursor = context.getContentResolver()
				.query(
						Events.CONTENT_URI,
						new String[]{_ID, TITLE, DTSTART, DTEND, EVENT_LOCATION, ALL_DAY, DISPLAY_COLOR, RRULE, DURATION}, selection,
						selectionArgs, null
				);
		cursor.moveToFirst();
		int count = cursor.getCount();
		if (count != 0) {
			//<editor-fold desc="Future Events">
			for (int i = 0; i < count; i++) {
				int id = cursor.getInt(0);
				String title = cursor.getString(1);
				long startDate = Long.parseLong(cursor.getString(2));
				String endDateString = cursor.getString(3);
				String location = cursor.getString(4);
				boolean isAllDay = cursor.getInt(5) != 0;
				int color = cursor.getInt(6);
				String rRule = cursor.getString(7);
				String duration = cursor.getString(8);
				String lowerCaseRule = rRule == null ? null : rRule.toLowerCase();
				boolean isInfinity = (lowerCaseRule != null && !(lowerCaseRule.contains("until") || lowerCaseRule.contains("count")));
				if (!isAllDay) {
					// If the event not repeat itself - regular event
					if (rRule == null) {
						long endDate = Long.parseLong(endDateString);
						events.add(new Event(id, title, startDate, endDate, location).setColor(color));
					} else {
						// Event that repeat itself
						events = addEvents(events, getEventFromRepeating(rRule, startDate, duration, location, color, title, id, false), isInfinity);
					}
				} else {
					if (rRule == null) {
						// One day event probably
						if (endDateString == null || Long.parseLong(endDateString) == 0)
							events.add(new Event(id, title, dayFormatter.format(new Date(startDate - timezone.getOffset(startDate))), location).setColor(color));
						else {
							int offset = timezone.getOffset(startDate);
							long newTime = startDate - offset;
							long endTime = Long.parseLong(endDateString) - offset;
							events.add(new Event(id, title, newTime, endTime, location).setColor(color).isAllDay(true));
						}
					} else {
						// Repeat all day event, god why?!?
						events = addEvents(events, getEventFromRepeating(rRule, startDate - timezone.getOffset(startDate), duration, location, color, title, id, true), isInfinity);
					}
				}
				cursor.moveToNext();
			}
			//</editor-fold>
		}
		cursor.close();
		String repeatingSections = "((" + DURATION + " IS NOT NULL) AND ((" + DTSTART + " < ?) OR (" + DTEND + " < ?)))";
		Cursor repeatingCursor = context.getContentResolver()
				.query(
						Events.CONTENT_URI,
						new String[]{_ID, TITLE, DTSTART, EVENT_LOCATION, ALL_DAY, DISPLAY_COLOR, RRULE, DURATION}, repeatingSections,
						selectionArgs, null
				);
		repeatingCursor.moveToFirst();
		int repeatingCount = repeatingCursor.getCount();
		if (repeatingCount != 0) {
			//<editor-fold desc="repeating past Events">
			for (int i = 0; i < repeatingCount; i++) {
				int id = repeatingCursor.getInt(0);
				String title = repeatingCursor.getString(1);
				long startDate = Long.parseLong(repeatingCursor.getString(2));
				String location = repeatingCursor.getString(3);
				boolean isAllDay = repeatingCursor.getInt(4) != 0;
				int color = repeatingCursor.getInt(5);
				String rRule = repeatingCursor.getString(6);
				String duration = repeatingCursor.getString(7);
				String lowerCaseRule = rRule.toLowerCase();
				boolean isInfinity = !(lowerCaseRule.contains("until") || lowerCaseRule.contains("count"));
				if (!isAllDay) {
					ArrayList<Event> repeatingEvents = getEventFromRepeating(rRule, startDate, duration, location, color, title, id, false);
					events = addEvents(events, repeatingEvents, isInfinity);
				} else {
					ArrayList<Event> repeatingEvents = getEventFromRepeating(rRule, startDate - timezone.getOffset(startDate), duration, location, color, title, id, true);
					events = addEvents(events, repeatingEvents, isInfinity);
				}
				repeatingCursor.moveToNext();
			}
			//</editor-fold>
			repeatingCursor.close();
		}
		Collections.sort(events, new Comparator<Event>() {
			@Override
			//an integer < 0 if lhs is less than rhs, 0 if they are equal, and > 0 if lhs is greater than rhs.
			public int compare(Event lhs, Event rhs) {
				int first = (lhs.getStartDate() - rhs.getStartDate()) < 0 ? -1 : lhs.getStartDate() == rhs.getStartDate() ? 0 : 1;
				int second = (lhs.getEndDate() - rhs.getEndDate()) < 0 ? -1 : lhs.getEndDate() == rhs.getEndDate() ? 0 : 1;
				return first != 0 ? first : second;
			}
		});
		return events;
	}

	private static ArrayList<Event> addEvents(ArrayList<Event> list, ArrayList<Event> toAdd, boolean isInfinity) {
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.MONTH, 6);
		long milli = DateUtils.clearTime(calendar).getTimeInMillis();
		long now = System.currentTimeMillis();
		for (Event event : toAdd) {
			if (event.getEndDate() <= milli && event.getEndDate() >= now) list.add(event);
		}
		return list;
	}

	private static ArrayList<Event> getEventFromRepeating(String rRule, long startDate, String duration, String location, int color, String title, int id, boolean isAllDay) {
		ArrayList<Event> events = new ArrayList<>();
		String lowerCaseRule = rRule.toLowerCase();
		if (!(lowerCaseRule.contains("until") || lowerCaseRule.contains("count"))) {
			// It is infinity event, GOD DAMN
			// We will give the event 15 times to being checked, otherwise fuck up
			rRule += ";COUNT=10";
		}
		try {
			// We need GMT to get the real date (probably a bug)
			DateIterable dateIterable = DateIteratorFactory.createDateIterable("RRULE:" + rRule, new Date(startDate), TimeZone.getTimeZone("GMT"), true);
			// Now we need to get the end date using the duration :/
			Duration smartDuration = new Duration();
			try {
				smartDuration.parse(duration);
			} catch (Exception e) {
				// Well, something is absolutely wrong
			}
			boolean isFirst = true;
			for (Date date : dateIterable) {
				Calendar calendar = Calendar.getInstance();
				calendar.setTime(date);
				if (!isFirst)
					calendar.add(Calendar.DAY_OF_YEAR, -1);
				else isFirst = false;
				long endTime = smartDuration.addTo(calendar.getTimeInMillis());
				events.add(new Event(id, title, calendar.getTimeInMillis(), endTime, location).setColor(color).isAllDay(isAllDay));
			}
		} catch (ParseException e) {
			// Well, something is absolutely wrong, let's blame LG for that
			e.printStackTrace();
		}
		// Fix a bug?!?
		if (events.size() >= 2) events.remove(1);
		return events;
	}

	public static String getDateFromEvent(Event event) {
		if (event.getDate() != null) return event.getDate();
		else if (event.isAllDay()) {
			Calendar startPlusOneDay = Calendar.getInstance();
			startPlusOneDay.setTimeInMillis(event.getStartDate());
			startPlusOneDay.add(Calendar.DAY_OF_YEAR, 1);
			Calendar endTime = Calendar.getInstance();
			endTime.setTimeInMillis(event.getEndDate());
			if (DateUtils.isSameDay(startPlusOneDay, endTime)) {
				startPlusOneDay.add(Calendar.DAY_OF_YEAR, -1);
				if (DateUtils.isToday(startPlusOneDay))
					return CalendarResources.today + " " + CalendarResources.allDay;
				else if (DateUtils.isTomorrow(startPlusOneDay))
					return CalendarResources.tomorrow + " " + CalendarResources.allDay;
				return dayFormatter.format(new Date(event.getStartDate()));
			} else {
				endTime.add(Calendar.DAY_OF_YEAR, -1);
				startPlusOneDay.add(Calendar.DAY_OF_YEAR, -1);
				if (DateUtils.isToday(startPlusOneDay)) {
					if (DateUtils.isTomorrow(endTime)) return CalendarResources.today + " - " + CalendarResources.tomorrow;
					else return CalendarResources.today + " " + CalendarResources.allDay + " - " + fullDateFormat.format(endTime.getTime());
				} else if (DateUtils.isTomorrow(startPlusOneDay))
					return CalendarResources.tomorrow + " - " + fullDateFormat.format(endTime.getTime());
				return fullDateFormat.format(new Date(event.getStartDate())) + " - " + fullDateFormat.format(endTime.getTime());
			}
		} else {
			String text;
			Date first = new Date(event.getStartDate());
			Date end = new Date(event.getEndDate());
			if (DateUtils.isSameDay(first, end)) {
				if (DateUtils.isToday(first))
					text = CalendarResources.today + " " + hourFormatter.format(first) + " - " + hourFormatter.format(end);
				else if (DateUtils.isWithinDaysFuture(first, 1))
					text = CalendarResources.tomorrow + hourFormatter.format(first) + " - " + hourFormatter.format(end);
				else
					text = dateFormatter.format(first) + " - " + hourFormatter.format(end);
			} else if (DateUtils.isToday(first)) {
				text = CalendarResources.today + hourFormatter.format(first) + " - " + otherDayFormatter.format(end);
			} else {
				text = otherDayFormatter.format(first) + " - " + otherDayFormatter.format(end);
			}
			return text;
		}
	}

	public static Intent launchEventById(long id) {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		Uri.Builder uri = Events.CONTENT_URI.buildUpon();
		uri.appendPath(Long.toString(id));
		intent.setData(uri.build());
		return intent;
	}

	public static String getTimeToEvent(Event event) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(event.getStartDate());
		Calendar now = Calendar.getInstance();
		now.set(Calendar.SECOND, 0);
		now.set(Calendar.MILLISECOND, 0);
		if (calendar.getTimeInMillis() <= now.getTimeInMillis()) return CalendarResources.now;
		else {
			long secondsLeft = (calendar.getTimeInMillis() - now.getTimeInMillis()) / 1000;
			if (secondsLeft < 60) return CalendarResources.in + " 1 " + CalendarResources.minute;
			long minutesLeft = secondsLeft / 60;
			if (minutesLeft < 60)
				return CalendarResources.in + " " + minutesLeft + " " + (minutesLeft > 1 ? CalendarResources.minutes : CalendarResources.minute);
			long hoursLeft = minutesLeft / 50;
			if (hoursLeft < 24) return CalendarResources.in + " " + hoursLeft + " " + (hoursLeft > 1 ? CalendarResources.hours : CalendarResources.hour);
			int days = (int) (hoursLeft / 24);
			if (days < 30) return CalendarResources.in + " " + days + " " + (days > 1 ? CalendarResources.days : CalendarResources.day);
			int months = days / 30;
			if (months < 12) return CalendarResources.in + " " + months + " " + (months > 1 ? CalendarResources.months : CalendarResources.month);
			else return CalendarResources.moreThenAYearLeft;
		}
	}

	public static class CalendarResources {
		public static String today;
		public static String tomorrow;
		public static String allDay;
		public static String now;
		public static String in;
		public static String minute;
		public static String minutes;
		public static String hour;
		public static String hours;
		public static String day;
		public static String days;
		public static String week;
		public static String weeks;
		public static String month;
		public static String months;
		public static String moreThenAYearLeft;

		public static void init(Context context) {
			if (today == null || moreThenAYearLeft == null) {
				today = context.getString(R.string.today);
				tomorrow = context.getString(R.string.tomorrow);
				allDay = context.getString(R.string.all_day);
				now = context.getString(R.string.now);
				in = context.getString(R.string.in);
				String[] min = context.getString(R.string.minute_s).split("/");
				minute = min[0];
				minutes = min[1];
				String[] hoursArray = context.getString(R.string.hour_s).split("/");
				hour = hoursArray[0];
				hours = hoursArray[1];
				String[] dayArray = context.getString(R.string.day_s).split("/");
				day = dayArray[0];
				days = dayArray[1];
				String[] weekArray = context.getString(R.string.week_s).split("/");
				week = weekArray[0];
				weeks = weekArray[1];
				String[] monthArray = context.getString(R.string.month_s).split("/");
				month = monthArray[0];
				months = monthArray[1];
				moreThenAYearLeft = context.getString(R.string.more_then_year);
			}
		}
	}

}
