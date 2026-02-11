/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package yancey.chelper.android.common.util;

import android.content.Context;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class HistoryManager {

    private static final int MAX_SIZE = 1000;
    private LinkedList<String> historyList;
    private final File file;

    private static HistoryManager INSTANCE;

    private HistoryManager(File file) {
        this.file = file;
        if (file.exists()) {
            String content = FileUtil.readString(file);
            if (content != null) {
                historyList = Arrays.stream(content.split("\n"))
                        .collect(LinkedList::new, LinkedList::add, LinkedList::addAll);
            }
        }
        if (historyList == null) {
            historyList = new LinkedList<>();
        }
    }

    public static HistoryManager getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = new HistoryManager(FileUtil.getFile(context.getDataDir(), "history.txt"));
        }
        return INSTANCE;
    }

    /**
     * 添加新内容到历史记录
     *
     * @param content 内容
     */
    public void add(@NotNull String content) {
        if (content.isEmpty()) {
            return;
        }
        historyList.remove(content);
        if (historyList.size() >= MAX_SIZE) {
            historyList.removeLast();
        }
        historyList.addFirst(content);
    }

    /**
     * 获取全部历史记录
     *
     * @return 历史记录列表
     */
    public List<String> getAll() {
        return new ArrayList<>(historyList);
    }

    /**
     * 获取当前历史记录数量
     *
     * @return 记录数量
     */
    public int size() {
        return historyList.size();
    }

    public void save() {
        FileUtil.writeString(file, String.join("\n", historyList));
    }
}