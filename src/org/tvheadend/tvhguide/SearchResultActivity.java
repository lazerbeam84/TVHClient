/*
 *  Copyright (C) 2011 John Törnblom
 *
 * This file is part of TVHGuide.
 *
 * TVHGuide is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TVHGuide is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with TVHGuide.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.tvheadend.tvhguide;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import org.tvheadend.tvhguide.htsp.HTSListener;
import org.tvheadend.tvhguide.htsp.HTSService;
import org.tvheadend.tvhguide.model.Channel;
import org.tvheadend.tvhguide.model.Program;
import org.tvheadend.tvhguide.model.Recording;

import android.app.Activity;
import android.app.ListActivity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

/**
 *
 * @author john-tornblom
 */
public class SearchResultActivity extends ListActivity implements HTSListener {

    private SearchResultAdapter srAdapter;
    private SparseArray<String> contentTypes;
    private Pattern pattern;
    private Channel channel;

    @Override
    public void onCreate(Bundle icicle) {

        // Apply the specified theme
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Boolean theme = prefs.getBoolean("lightThemePref", false);
        setTheme(theme ? android.R.style.Theme_Holo_Light : android.R.style.Theme_Holo);

        super.onCreate(icicle);

        // Setup the action bar and show the title
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeButtonEnabled(true);
        getActionBar().setTitle("Searching");
        
        registerForContextMenu(getListView());
        contentTypes = TVHGuideApplication.getContentTypes(this);

        List<Program> srList = new ArrayList<Program>();
        srAdapter = new SearchResultAdapter(this, srList);
        srAdapter.sort();
        setListAdapter(srAdapter);

        onNewIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (!Intent.ACTION_SEARCH.equals(intent.getAction()) || !intent.hasExtra(SearchManager.QUERY)) {
            return;
        }

        TVHGuideApplication app = (TVHGuideApplication) getApplication();
        Bundle appData = intent.getBundleExtra(SearchManager.APP_DATA);
        if (appData != null) {
            channel = app.getChannel(appData.getLong("channelId"));
        } else {
            channel = null;
        }

        srAdapter.clear();

        String query = intent.getStringExtra(SearchManager.QUERY);
        pattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE);
        intent = new Intent(SearchResultActivity.this, HTSService.class);
        intent.setAction(HTSService.ACTION_EPG_QUERY);
        intent.putExtra("query", query);
        if (channel != null) {
            intent.putExtra("channelId", channel.id);
        }

        startService(intent);

        if (channel == null) {
            for (Channel ch : app.getChannels()) {
                for (Program p : ch.epg) {
                    if (pattern.matcher(p.title).find()) {
                        srAdapter.add(p);
                    }
                }
            }
        } else {
            for (Program p : channel.epg) {
                if (pattern.matcher(p.title).find()) {
                    srAdapter.add(p);
                }
            }
        }

        getActionBar().setTitle(android.R.string.search_go);
        getActionBar().setSubtitle(query);
    }

    @Override
    protected void onResume() {
        super.onResume();
        TVHGuideApplication app = (TVHGuideApplication) getApplication();
        app.addListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        TVHGuideApplication app = (TVHGuideApplication) getApplication();
        app.removeListener(this);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Program p = (Program) srAdapter.getItem(position);

        Intent intent = new Intent(this, ProgramActivity.class);
        intent.putExtra("eventId", p.id);
        intent.putExtra("channelId", p.channel.id);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.search_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            onBackPressed();
            return true;
        case R.id.menu_search:
            // Show the search text input in the action bar
            onSearchRequested();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        
            case R.string.menu_record:
            case R.string.menu_record_cancel:
            case R.string.menu_record_remove: {
                startService(item.getIntent());
                return true;
            }
            default: {
                return super.onContextItemSelected(item);
            }
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        Program p = srAdapter.getItem(info.position);

        menu.setHeaderTitle(p.title);

        Intent intent = new Intent(this, HTSService.class);

        MenuItem item = null;

        if (p.recording == null) {
            intent.setAction(HTSService.ACTION_DVR_ADD);
            intent.putExtra("eventId", p.id);
            intent.putExtra("channelId", p.channel.id);
            item = menu.add(ContextMenu.NONE, R.string.menu_record, ContextMenu.NONE, R.string.menu_record);
        } else if ("recording".equals(p.recording.state) || "scheduled".equals(p.recording.state)) {
            intent.setAction(HTSService.ACTION_DVR_CANCEL);
            intent.putExtra("id", p.recording.id);
            item = menu.add(ContextMenu.NONE, R.string.menu_record_cancel, ContextMenu.NONE, R.string.menu_record_cancel);
        } else {
            intent.setAction(HTSService.ACTION_DVR_DELETE);
            intent.putExtra("id", p.recording.id);
            item = menu.add(ContextMenu.NONE, R.string.menu_record_remove, ContextMenu.NONE, R.string.menu_record_remove);
        }

        item.setIntent(intent);
    }

    public void onMessage(String action, final Object obj) {
        if (action.equals(TVHGuideApplication.ACTION_PROGRAMME_ADD)) {
            runOnUiThread(new Runnable() {

                public void run() {
                    Program p = (Program) obj;
                    if (pattern != null && pattern.matcher(p.title).find()) {
                        srAdapter.add(p);
                        srAdapter.notifyDataSetChanged();
                        srAdapter.sort();
                    }
                }
            });
        } else if (action.equals(TVHGuideApplication.ACTION_PROGRAMME_DELETE)) {
            runOnUiThread(new Runnable() {

                public void run() {
                    Program p = (Program) obj;
                    srAdapter.remove(p);
                    srAdapter.notifyDataSetChanged();
                }
            });
        } else if (action.equals(TVHGuideApplication.ACTION_PROGRAMME_UPDATE)) {
            runOnUiThread(new Runnable() {

                public void run() {
                    Program p = (Program) obj;
                    srAdapter.updateView(getListView(), p);
                }
            });
        } else if (action.equals(TVHGuideApplication.ACTION_DVR_UPDATE)) {
            runOnUiThread(new Runnable() {

                public void run() {
                    Recording rec = (Recording) obj;
                    for (Program p : srAdapter.list) {
                        if (rec == p.recording) {
                            srAdapter.updateView(getListView(), p);
                            return;
                        }
                    }
                }
            });
        }
    }
	
    private class ViewWarpper {
        Context ctx;
        TextView title;
        TextView channel;
        TextView time;
        TextView date;
        TextView duration;
        TextView description;
        ImageView icon;
        ImageView state;

        public ViewWarpper(Context context, View base) {
            ctx = context;
            title = (TextView) base.findViewById(R.id.sr_title);
            channel = (TextView) base.findViewById(R.id.sr_channel);
            description = (TextView) base.findViewById(R.id.sr_desc);

            time = (TextView) base.findViewById(R.id.sr_time);
            date = (TextView) base.findViewById(R.id.sr_date);
            duration = (TextView) base.findViewById(R.id.sr_duration);
            icon = (ImageView) base.findViewById(R.id.sr_icon);
            state = (ImageView) base.findViewById(R.id.sr_state);
        }

        public void repaint(Program p) {
            Channel ch = p.channel;

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(icon.getContext());
            Boolean showIcons = prefs.getBoolean("showIconPref", false);
            icon.setVisibility(showIcons ? ImageView.VISIBLE : ImageView.GONE);
            icon.setImageBitmap(ch.iconBitmap);

            title.setText(p.title);

            if (p.recording == null) {
                state.setImageDrawable(null);
            } else if (p.recording.error != null) {
                state.setImageResource(R.drawable.ic_error_small);
            } else if ("completed".equals(p.recording.state)) {
                state.setImageResource(R.drawable.ic_success_small);
            } else if ("invalid".equals(p.recording.state)) {
                state.setImageResource(R.drawable.ic_error_small);
            } else if ("missed".equals(p.recording.state)) {
                state.setImageResource(R.drawable.ic_error_small);
            } else if ("recording".equals(p.recording.state)) {
                state.setImageResource(R.drawable.ic_rec_small);
            } else if ("scheduled".equals(p.recording.state)) {
                state.setImageResource(R.drawable.ic_schedule_small);
            } else {
                state.setImageDrawable(null);
            }

            title.invalidate();

            String s = Utils.buildSeriesInfoString(ctx, p.seriesInfo);
            if(s.length() == 0) {
            	s = p.description;
            }
            
            // Get the start and end times so we can show them 
            // and calculate the duration. Then show the duration in minutes
            double durationTime = (p.stop.getTime() - p.start.getTime());
            durationTime = (durationTime / 1000 / 60);
            if (durationTime > 0) {
                duration.setText(duration.getContext().getString(R.string.ch_minutes, (int)durationTime));
            } else {
                duration.setVisibility(View.GONE);
            }
            duration.invalidate();
            
            description.setText(s);
            description.invalidate();
            
            String contentType = contentTypes.get(p.contentType, "");
            if (contentType.length() > 0) {
                channel.setText(ch.name + " (" + contentType + ")");
            } else {
                channel.setText(ch.name);
            }
            channel.invalidate();

            date.setText(Utils.getStartDate(ctx, p.start));
            date.invalidate();

            
            time.setText(
                    DateFormat.getTimeFormat(time.getContext()).format(p.start)
                    + " - "
                    + DateFormat.getTimeFormat(time.getContext()).format(p.stop));
            time.invalidate();
        }
    }

    class SearchResultAdapter extends ArrayAdapter<Program> {

        Activity context;
        List<Program> list;

        SearchResultAdapter(Activity context, List<Program> list) {
            super(context, R.layout.search_result_widget, list);
            this.context = context;
            this.list = list;
        }

        public void sort() {
            sort(new Comparator<Program>() {

                public int compare(Program x, Program y) {
                    return x.compareTo(y);
                }
            });
        }

        public void updateView(ListView listView, Program programme) {
            for (int i = 0; i < listView.getChildCount(); i++) {
                View view = listView.getChildAt(i);
                int pos = listView.getPositionForView(view);
                Program pr = (Program) listView.getItemAtPosition(pos);

                if (view.getTag() == null || pr == null) {
                    continue;
                }

                if (programme.id != pr.id) {
                    continue;
                }

                ViewWarpper wrapper = (ViewWarpper) view.getTag();
                wrapper.repaint(programme);
                break;
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            ViewWarpper wrapper = null;

            if (row == null) {
                LayoutInflater inflater = context.getLayoutInflater();
                row = inflater.inflate(R.layout.search_result_widget, null, false);

                wrapper = new ViewWarpper(this.getContext(), row);
                row.setTag(wrapper);

            } else {
                wrapper = (ViewWarpper) row.getTag();
            }

            Program p = getItem(position);
            wrapper.repaint(p);
            return row;
        }
    }
}
