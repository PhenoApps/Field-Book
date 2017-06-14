/* 
 * Sortable Preference ListView. Allows for sorting items in a view,
 * and selecting which ones to use. 
 * 
 * Example Usage (In a preference file)
 * 
 * 	<com.mobeta.android.demodslv.SortableListPreference
 * 		android:defaultValue="@array/pref_name_defaults"
 * 		android:entries="@array/pref_name_titles"
 * 		android:entryValues="@array/pref_name_values"
 * 		android:key="name_order"
 * 		android:persistent="true"
 * 		android:title="@string/pref_name_selection" />
 * 
 * Original Source: https://github.com/kd7uiy/drag-sort-listview
 * 
 * The MIT License (MIT)
 * 
 * Copyright (c) 2013 The Making of a Ham, http://www.kd7uiy.com
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * 
 * Code snippets copied from the following sources:
 * https://gist.github.com/cardil/4754571
 * 
 * 
 */

package com.fieldbook.tracker.dragsort;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.res.TypedArray;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;

import com.fieldbook.tracker.R;

public class DragSortListPreference extends ListPreference {
	private static final String TAG = DragSortListPreference.class.getName();

	protected DragSortListView mListView;
	protected ArrayAdapter<CharSequence> mAdapter;

	public static final String DEFAULT_SEPARATOR = "\u0001\u0007\u001D\u0007\u0001";
	private String mSeparator;

	private HashMap<CharSequence, Boolean> entryChecked;

	private int mListPreference;

	private int mAdapterView;

	private int mTextField;

	public DragSortListPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		mSeparator = DEFAULT_SEPARATOR;
		if (attrs != null) {
			TypedArray a = getContext().obtainStyledAttributes(attrs,
					R.styleable.DragSortListPreference, 0, 0);

			for (int i = 0; i < attrs.getAttributeCount(); i++) {
				Log.v(TAG,
						attrs.getAttributeName(i) + "="
								+ attrs.getAttributeNameResource(i));
			}

			mListPreference = a.getResourceId(
					R.styleable.DragSortListPreference_pref_layout,
					R.layout.sort_list_array_dialog_preference);
			mAdapterView = a.getResourceId(
					R.styleable.DragSortListPreference_array_adapter_view,
					android.R.layout.simple_list_item_1);
			mTextField = a.getResourceId(
					R.styleable.DragSortListPreference_text_field,
					android.R.id.text1);

			Log.v(TAG, "mListPref=" + mListPreference);
			Log.v(TAG, "mAdapterView=" + mAdapterView);
			Log.v(TAG, "mTextField=" + mTextField);

			a.recycle();
		}
		setDialogLayoutResource(mListPreference);
		entryChecked = new HashMap<CharSequence, Boolean>();
	}

	public static CharSequence[] decodeValue(String input) {
		return decodeValue(input, DEFAULT_SEPARATOR);
	}

	public static CharSequence[] decodeValue(String input, String separator) {
		if (input == null) {
			return null;
		}
		if (input.equals("")) {
			return new CharSequence[0];
		}
		return input.split(separator);
	}

	@Override
	protected void onBindDialogView(View view) {
		super.onBindDialogView(view);
		mListView = (DragSortListView) view.findViewById(android.R.id.list);
		mAdapter = new ArrayAdapter<CharSequence>(mListView.getContext(),
				mAdapterView, mTextField);
		mListView.setAdapter(mAdapter);
		// This will drop the item in the new location
		mListView.setDropListener(new DragSortListView.DropListener() {
			@Override
			public void drop(int from, int to) {
				CharSequence item = mAdapter.getItem(from);
				mAdapter.remove(item);
				mAdapter.insert(item, to);
				// Updates checked states
				mListView.moveCheckState(from, to);
			}
		});
		// Setting the default values happens in onPrepareDialogBuilder
	}

	@Override
	protected void onPrepareDialogBuilder(Builder builder) {
		Log.v(TAG, "onPrepareDialogBuilder");

		CharSequence[] entries = getEntries();
		CharSequence[] entryValues = getEntryValues();
		if (entries == null || entryValues == null
				|| entries.length != entryValues.length) {
			throw new IllegalStateException(
					"SortableListPreference requires an entries array and an entryValues "
							+ "array which are both the same length");
		}

		CharSequence[] restoredValues = restoreEntries();
		for (CharSequence value : restoredValues) {
			mAdapter.add(entries[getValueIndex(value)]);
		}

	}

	@Override
	protected void onDialogClosed(boolean positiveResult) {
		List<CharSequence> values = new ArrayList<CharSequence>();

		CharSequence[] entryValues = getEntryValues();
		if (positiveResult && entryValues != null) {
			for (int i = 0; i < entryValues.length; i++) {
				String val = (String) mAdapter.getItem(i);
				boolean isChecked = mListView.isItemChecked(i);
				if (isChecked) {
					values.add(entryValues[getValueTitleIndex(val)]);
				}
			}

			String value = join(values, mSeparator);
			setSummary(prepareSummary(values));
			setValueAndEvent(value);
		}
	}

	private void setValueAndEvent(String value) {
		if (callChangeListener(decodeValue(value, mSeparator))) {
			setValue(value);
		}
	}

	@Override
	protected Object onGetDefaultValue(TypedArray typedArray, int index) {
		return typedArray.getTextArray(index);
	}

	@Override
	protected void onSetInitialValue(boolean restoreValue,
			Object rawDefaultValue) {
		String value = null;
		CharSequence[] defaultValue;
		if (rawDefaultValue == null) {
			defaultValue = new CharSequence[0];
		} else {
			defaultValue = (CharSequence[]) rawDefaultValue;
		}
		List<CharSequence> joined = Arrays.asList(defaultValue);
		String joinedDefaultValue = join(joined, mSeparator);
		if (restoreValue) {
			value = getPersistedString(joinedDefaultValue);
		} else {
			value = joinedDefaultValue;
		}

		setSummary(prepareSummary(Arrays.asList(decodeValue(value, mSeparator))));
		setValueAndEvent(value);
	}

	private String prepareSummary(List<CharSequence> joined) {
		List<String> titles = new ArrayList<String>();
		CharSequence[] entryTitle = getEntries();
		for (CharSequence item : joined) {
			int ix = getValueIndex(item);
			titles.add((String) entryTitle[ix]);
		}
		return join(titles, ", ");
	}

	public int getValueIndex(CharSequence item) {
		CharSequence[] entryValues = getEntryValues();
		for (int i = 0; i < entryValues.length; i++) {
			if (entryValues[i].equals(item)) {
				return i;
			}
		}
		throw new IllegalStateException(item + " not found in value list");
	}

	public int getValueTitleIndex(CharSequence item) {
		CharSequence[] entries = getEntries();
		for (int i = 0; i < entries.length; i++) {
			if (entries[i].equals(item)) {
				return i;
			}
		}
		throw new IllegalStateException(item + " not found in value title list");
	}

	private CharSequence[] restoreEntries() {

		ArrayList<CharSequence> orderedList = new ArrayList<CharSequence>();

		// Initially populated with all of the values in the determined list.
		CharSequence[] values = decodeValue(getValue(), mSeparator);
		for (int ix = 0; ix < values.length; ix++) {
			CharSequence value = values[ix];
			orderedList.add(value);
			mListView.setItemChecked(ix, true);
		}

		// This loop sets the default states, and adds to the name list if not
		// on the list.
		for (CharSequence value : getEntryValues()) {
			entryChecked.put(value, false);
			if (!orderedList.contains(value)) {
				orderedList.add(value);
			}
		}
		for (CharSequence value : orderedList) {
			if (entryChecked.containsKey(value)) {
				entryChecked.put(value, true);
			} else {
				throw new IllegalArgumentException("Invalid value " + value
						+ " in key list");
			}
		}
		return orderedList.toArray(new CharSequence[0]);
	}

	/**
	 * Joins array of object to single string by separator
	 * 
	 * Credits to kurellajunior on this post
	 * http://snippets.dzone.com/posts/show/91
	 * 
	 * @param iterable
	 *            any kind of iterable ex.: <code>["a", "b", "c"]</code>
	 * @param separator
	 *            Separates entries ex.: <code>","</code>
	 * @return joined string ex.: <code>"a,b,c"</code>
	 */
	protected static String join(Iterable<?> iterable, String separator) {
		Iterator<?> oIter;
		if (iterable == null || (!(oIter = iterable.iterator()).hasNext()))
			return "";
		StringBuilder oBuilder = new StringBuilder(String.valueOf(oIter.next()));
		while (oIter.hasNext())
			oBuilder.append(separator).append(oIter.next());
		return oBuilder.toString();
	}
}
