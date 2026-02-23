/*
 * Copyright (C) 2025 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.ui.widget;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.util.UIUtils;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

public class StopPickerActivity extends AppCompatActivity
        implements LoaderManager.LoaderCallbacks<Cursor> {

    public static final String EXTRA_STOP_ID = "stop_id";
    public static final String EXTRA_STOP_NAME = "stop_name";

    private static final int LOADER_STARRED = 0;
    private static final int LOADER_RECENT = 1;

    private static final String[] STOP_PROJECTION = {
            ObaContract.Stops._ID,       // 0
            ObaContract.Stops.UI_NAME,   // 1
            ObaContract.Stops.DIRECTION, // 2
            ObaContract.Stops.FAVORITE,  // 3
    };
    private static final int COL_ID = 0;
    private static final int COL_NAME = 1;
    private static final int COL_DIRECTION = 2;
    private static final int COL_FAVORITE = 3;

    private StopPickerAdapter mAdapter;
    private String mSearchQuery = "";
    private Cursor mStarredCursor;
    private Cursor mRecentCursor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stop_picker);
        setTitle(R.string.stop_picker_title);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        ListView listView = findViewById(R.id.stop_list);
        TextView emptyView = findViewById(android.R.id.empty);

        mAdapter = new StopPickerAdapter(this);
        listView.setAdapter(mAdapter);
        listView.setEmptyView(emptyView);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            StopPickerAdapter.StopItem stop = mAdapter.getStop(position);
            if (stop == null) return;
            Intent result = new Intent();
            result.putExtra(EXTRA_STOP_ID, stop.stopId);
            result.putExtra(EXTRA_STOP_NAME, stop.stopName);
            setResult(RESULT_OK, result);
            finish();
        });

        EditText searchBox = findViewById(R.id.search_box);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                mSearchQuery = s.toString().trim();
                getSupportLoaderManager().restartLoader(LOADER_STARRED, null, StopPickerActivity.this);
                getSupportLoaderManager().restartLoader(LOADER_RECENT, null, StopPickerActivity.this);
            }
        });

        getSupportLoaderManager().initLoader(LOADER_STARRED, null, this);
        getSupportLoaderManager().initLoader(LOADER_RECENT, null, this);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, @Nullable Bundle args) {
        StringBuilder where = new StringBuilder();
        List<String> argList = new ArrayList<>();

        long regionId = Application.get().getCurrentRegion() != null
                ? Application.get().getCurrentRegion().getId() : -1;

        if (id == LOADER_STARRED) {
            where.append(ObaContract.Stops.FAVORITE).append("=1");
        } else {
            where.append(ObaContract.Stops.FAVORITE).append("!=1")
                 .append(" AND (").append(ObaContract.Stops.ACCESS_TIME).append(" IS NOT NULL")
                 .append(" OR ").append(ObaContract.Stops.USE_COUNT).append(">0)");
        }

        if (regionId >= 0) {
            where.append(" AND ").append(ObaContract.Stops.REGION_ID).append("=").append(regionId);
        }

        if (!mSearchQuery.isEmpty()) {
            where.append(" AND ").append(ObaContract.Stops.UI_NAME).append(" LIKE ?");
            argList.add("%" + mSearchQuery + "%");
        }

        String[] selArgs = argList.isEmpty() ? null : argList.toArray(new String[0]);
        String orderBy = id == LOADER_STARRED
                ? ObaContract.Stops.UI_NAME + " ASC"
                : ObaContract.Stops.ACCESS_TIME + " DESC, " + ObaContract.Stops.USE_COUNT + " DESC";
        android.net.Uri uri = id == LOADER_RECENT
                ? ObaContract.Stops.CONTENT_URI.buildUpon().appendQueryParameter("limit", "20").build()
                : ObaContract.Stops.CONTENT_URI;

        return new CursorLoader(this, uri, STOP_PROJECTION, where.toString(), selArgs, orderBy);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
        if (loader.getId() == LOADER_STARRED) mStarredCursor = data;
        else mRecentCursor = data;
        rebuildList();
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        if (loader.getId() == LOADER_STARRED) mStarredCursor = null;
        else mRecentCursor = null;
        rebuildList();
    }

    private void rebuildList() {
        mAdapter.setData(mStarredCursor, mRecentCursor);
    }

    private static class StopPickerAdapter extends BaseAdapter {

        private static final int TYPE_HEADER = 0;
        private static final int TYPE_STOP = 1;

        static class ListItem {
            final int type;
            final String label;
            final StopItem stop;

            ListItem(String label) {
                this.type = TYPE_HEADER;
                this.label = label;
                this.stop = null;
            }

            ListItem(StopItem stop) {
                this.type = TYPE_STOP;
                this.label = null;
                this.stop = stop;
            }
        }

        static class StopItem {
            final String stopId;
            final String stopName;
            final String direction;
            final boolean isFavorite;

            StopItem(String stopId, String stopName, String direction, boolean isFavorite) {
                this.stopId = stopId;
                this.stopName = stopName;
                this.direction = direction;
                this.isFavorite = isFavorite;
            }
        }

        private final Context mContext;
        private final String mSectionStarred;
        private final String mSectionRecent;
        private final List<ListItem> mItems = new ArrayList<>();

        StopPickerAdapter(Context context) {
            mContext = context;
            mSectionStarred = context.getString(R.string.stop_picker_section_starred);
            mSectionRecent = context.getString(R.string.stop_picker_section_recent);
        }

        void setData(@Nullable Cursor starred, @Nullable Cursor recent) {
            mItems.clear();

            if (starred != null && starred.getCount() > 0 && starred.moveToFirst()) {
                mItems.add(new ListItem(mSectionStarred));
                do {
                    mItems.add(new ListItem(cursorToStop(starred)));
                } while (starred.moveToNext());
            }

            if (recent != null && recent.getCount() > 0 && recent.moveToFirst()) {
                mItems.add(new ListItem(mSectionRecent));
                do {
                    mItems.add(new ListItem(cursorToStop(recent)));
                } while (recent.moveToNext());
            }

            notifyDataSetChanged();
        }

        private StopItem cursorToStop(Cursor c) {
            return new StopItem(
                    c.getString(COL_ID),
                    c.getString(COL_NAME),
                    c.getString(COL_DIRECTION),
                    c.getInt(COL_FAVORITE) == 1
            );
        }

        @Nullable
        StopItem getStop(int position) {
            if (position < 0 || position >= mItems.size()) return null;
            ListItem item = mItems.get(position);
            return item.type == TYPE_STOP ? item.stop : null;
        }

        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int position) { return mItems.get(position).type; }
        @Override public boolean isEnabled(int position) { return getItemViewType(position) == TYPE_STOP; }
        @Override public int getCount() { return mItems.size(); }
        @Override public Object getItem(int position) { return mItems.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ListItem item = mItems.get(position);
            LayoutInflater inflater = LayoutInflater.from(mContext);

            if (item.type == TYPE_HEADER) {
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.list_item_section, parent, false);
                }
                ((TextView) convertView.findViewById(R.id.list_item_section_text)).setText(item.label);
                return convertView;
            }

            if (convertView == null) {
                convertView = inflater.inflate(R.layout.stop_list_item, parent, false);
            }

            StopItem stop = item.stop;
            ((TextView) convertView.findViewById(R.id.stop_name)).setText(stop.stopName);
            UIUtils.setStopDirection(convertView.findViewById(R.id.direction), stop.direction, true);

            ImageView favoriteIcon = convertView.findViewById(R.id.stop_favorite);
            if (stop.isFavorite) {
                favoriteIcon.setVisibility(View.VISIBLE);
                favoriteIcon.setColorFilter(
                        favoriteIcon.getResources().getColor(R.color.navdrawer_icon_tint));
            } else {
                favoriteIcon.setVisibility(View.GONE);
            }
            return convertView;
        }
    }
}