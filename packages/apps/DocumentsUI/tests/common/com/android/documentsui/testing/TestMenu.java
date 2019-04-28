/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui.testing;

import android.util.SparseArray;
import android.view.Menu;

import com.android.documentsui.R;

import org.mockito.Mockito;

/**
 *
 * Test copy of {@link android.view.Menu}.
 *
 * We use abstract so we don't have to implement all the necessary methods from the interface,
 * and we use Mockito to just mock out the methods we need.
 * To get an instance, use {@link #create(int...)}.
 */
public abstract class TestMenu implements Menu {

    private SparseArray<TestMenuItem> items = new SparseArray<>();

    public static TestMenu create() {
        return create(
                R.id.dir_menu_share,
                R.id.dir_menu_open,
                R.id.dir_menu_open_with,
                R.id.dir_menu_cut_to_clipboard,
                R.id.dir_menu_copy_to_clipboard,
                R.id.dir_menu_paste_from_clipboard,
                R.id.dir_menu_create_dir,
                R.id.dir_menu_select_all,
                R.id.dir_menu_rename,
                R.id.dir_menu_delete,
                R.id.dir_menu_view_in_owner,
                R.id.dir_menu_open_in_new_window,
                R.id.dir_menu_paste_into_folder,
                R.id.root_menu_eject_root,
                R.id.root_menu_open_in_new_window,
                R.id.root_menu_paste_into_folder,
                R.id.root_menu_settings,
                R.id.action_menu_open,
                R.id.action_menu_open_with,
                R.id.action_menu_share,
                R.id.action_menu_delete,
                R.id.action_menu_select_all,
                R.id.action_menu_copy_to,
                R.id.action_menu_extract_to,
                R.id.action_menu_move_to,
                R.id.action_menu_compress,
                R.id.action_menu_rename,
                R.id.action_menu_inspector,
                R.id.action_menu_view_in_owner,
                R.id.option_menu_search,
                R.id.option_menu_debug,
                R.id.option_menu_grid,
                R.id.option_menu_list,
                R.id.option_menu_new_window,
                R.id.option_menu_create_dir,
                R.id.option_menu_select_all,
                R.id.option_menu_advanced,
                R.id.option_menu_settings);
    }



    public static TestMenu create(int... ids) {
        final TestMenu menu = Mockito.mock(TestMenu.class,
                Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
        menu.items = new SparseArray<>();
        for (int id : ids) {
            TestMenuItem item = TestMenuItem.create(id);
            menu.addMenuItem(id, item);
        }
        return menu;
    }

    public void addMenuItem(int id, TestMenuItem item) {
        items.put(id, item);
    }

    @Override
    public TestMenuItem findItem(int id) {
        return items.get(id);
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public TestMenuItem getItem(int index) {
        return items.valueAt(index);
    }
}
