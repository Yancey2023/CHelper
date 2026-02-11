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

package yancey.chelper.android.completion.view;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hjq.toast.Toaster;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;

import yancey.chelper.R;
import yancey.chelper.android.common.util.ClipboardUtil;
import yancey.chelper.android.common.util.FileUtil;
import yancey.chelper.android.common.util.HistoryManager;
import yancey.chelper.android.common.util.MonitorUtil;
import yancey.chelper.android.common.util.Settings;
import yancey.chelper.android.common.view.BaseView;
import yancey.chelper.android.common.view.CommandEditText;
import yancey.chelper.android.completion.adater.SuggestionListAdapter;
import yancey.chelper.android.library.view.LocalLibraryListView;
import yancey.chelper.core.CHelperCore;
import yancey.chelper.core.CHelperGuiCore;
import yancey.chelper.core.CommandGuiCoreInterface;
import yancey.chelper.core.ErrorReason;
import yancey.chelper.core.SelectedString;
import yancey.chelper.core.Theme;

@SuppressLint("ViewConstructor")
public class CompletionView extends BaseView {

    private static final String TAG = "WritingCommandView";
    private final FrameLayout fl_action_container, fl_actions;
    private final View btn_action;
    private final CommandEditText commandEditText;
    private boolean isShowActions = false;
    public boolean isGuiLoaded;
    private final CHelperGuiCore core;
    private final HistoryManager historyManager;

    public CompletionView(
            @NonNull FWSContext fwsContext,
            @NonNull Runnable shutdown,
            @Nullable Runnable hideView
    ) {
        super(fwsContext, Settings.INSTANCE.isCrowed ? R.layout.layout_completion_crowded : R.layout.layout_completion);
        historyManager = HistoryManager.getInstance(context);
        boolean isCrowed = Settings.INSTANCE.isCrowed;
        isGuiLoaded = false;
        boolean isDarkMode = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        core = new CHelperGuiCore();
        @Nullable TextView tv_structure = view.findViewById(R.id.structure);
        @Nullable TextView tv_paramHint = view.findViewById(R.id.param_hint);
        @Nullable TextView tv_errorReasons = view.findViewById(R.id.error_reasons);
        commandEditText = view.findViewById(R.id.expression);
        commandEditText.setListener(s -> core.onSelectionChanged(), core::onSelectionChanged, () -> isGuiLoaded);
        commandEditText.setTheme(isDarkMode ? Theme.THEME_NIGHT : Theme.THEME_DAY);
        SuggestionListAdapter adapter = new SuggestionListAdapter(context, core, isCrowed);
        RecyclerView recyclerView = view.findViewById(R.id.rv_command_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);
        core.setCommandGuiCoreInterface(new CommandGuiCoreInterface() {
            @Override
            public boolean isUpdateStructure() {
                return true;
            }

            @Override
            public boolean isUpdateParamHint() {
                return true;
            }

            @Override
            public boolean isUpdateErrorReason() {
                return Settings.INSTANCE.isShowErrorReason || isSyntaxHighlight();
            }

            @Override
            public boolean isCheckingBySelection() {
                return Settings.INSTANCE.isCheckingBySelection;
            }

            @Override
            public boolean isSyntaxHighlight() {
                if (!Settings.INSTANCE.isSyntaxHighlight) {
                    return false;
                }
                Editable text = commandEditText.getText();
                return text != null && text.length() < 200;
            }

            @Override
            public void updateStructure(@Nullable String structure) {
                if (tv_structure != null) {
                    tv_structure.setText(structure);
                }
                adapter.setStructure(structure);
            }

            @Override
            public void updateParamHint(@Nullable String paramHint) {
                if (tv_paramHint != null) {
                    tv_paramHint.setText(paramHint);
                }
                adapter.setParamHint(paramHint);
            }

            @Override
            public void updateErrorReason(@Nullable ErrorReason[] errorReasons) {
                if (Settings.INSTANCE.isShowErrorReason) {
                    String errorReasonStr;
                    if (errorReasons == null || errorReasons.length == 0) {
                        errorReasonStr = null;
                    } else {
                        if (errorReasons.length == 1) {
                            errorReasonStr = errorReasons[0].errorReason;
                        } else {
                            StringBuilder errorReasonStrBuilder = new StringBuilder("可能的错误原因：");
                            for (int i = 0; i < errorReasons.length; i++) {
                                errorReasonStrBuilder.append("\n").append(i + 1).append(". ").append(errorReasons[i].errorReason);
                            }
                            errorReasonStr = errorReasonStrBuilder.toString();
                        }
                    }
                    if (tv_errorReasons != null) {
                        if (errorReasonStr == null) {
                            tv_errorReasons.setVisibility(View.GONE);
                        } else {
                            tv_errorReasons.setVisibility(View.VISIBLE);
                            tv_errorReasons.setText(errorReasonStr);
                        }
                    }
                    adapter.setErrorReasons(errorReasonStr);
                }
                commandEditText.setErrorReasons(isSyntaxHighlight() ? errorReasons : null);
            }

            @Override
            @SuppressLint("NotifyDataSetChanged")
            public void updateSuggestions() {
                adapter.notifyDataSetChanged();
            }

            @NonNull
            @Override
            public SelectedString getSelectedString() {
                return commandEditText.getSelectedString();
            }

            @Override
            public void setSelectedString(@NonNull SelectedString selectedString) {
                commandEditText.setSelectedString(selectedString);
            }

            @Override
            public void updateSyntaxHighlight(int[] tokens) {
                commandEditText.setColors(tokens);
            }
        });
        btn_action = view.findViewById(R.id.btn_show_menu);
        btn_action.setOnClickListener(v -> {
            isShowActions = !isShowActions;
            updateActions();
        });
        view.findViewById(R.id.btn_copy).setOnClickListener(v -> {
            Editable text = commandEditText.getText();
            if (text == null) {
                return;
            }
            historyManager.add(text.toString());
            if (ClipboardUtil.setText(getContext(), text)) {
                Toaster.show("已复制");
                if (hideView != null && Settings.INSTANCE.isHideWindowWhenCopying) {
                    hideView.run();
                }
            } else {
                Toaster.show("复制失败");
            }
        });
        view.findViewById(R.id.btn_undo).setOnClickListener(v -> commandEditText.undo());
        view.findViewById(R.id.btn_redo).setOnClickListener(v -> commandEditText.redo());
        view.findViewById(R.id.btn_clear).setOnClickListener(v -> commandEditText.clear());
        view.findViewById(R.id.btn_history).setOnClickListener(v -> openView(context -> new HistoryView(context, historyManager)));
        view.findViewById(R.id.btn_local_library).setOnClickListener(v -> openView(LocalLibraryListView::new));
        view.findViewById(R.id.btn_shut_down).setOnClickListener(v -> shutdown.run());
        // 加载上次的输入内容
        SelectedString selectedString = null;
        if (Settings.INSTANCE.isSavingWhenPausing) {
            File file = FileUtil.getFile(context.getFilesDir().getAbsolutePath(), "cache", "lastInput.dat");
            if (file.exists()) {
                try (DataInputStream dataInputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
                    selectedString = new SelectedString(dataInputStream.readUTF(), dataInputStream.readInt(), dataInputStream.readInt());
                } catch (IOException e) {
                    Log.e(TAG, "fail to save file : " + file.getAbsolutePath(), e);
                    MonitorUtil.generateCustomLog(e, "IOException");
                }
            }
        }
        if (selectedString == null) {
            selectedString = new SelectedString("", 0);
        }
        SelectedString finalSelectedString = selectedString;
        view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                isGuiLoaded = true;
                view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                commandEditText.setSelectedString(finalSelectedString);
                core.onSelectionChanged();
            }
        });
        fl_action_container = view.findViewById(R.id.fl_action_container);
        fl_actions = view.findViewById(R.id.fl_actions);
        if (!isShowActions) {
            updateActions();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        isGuiLoaded = false;
        historyManager.save();
        // 保存上次的输入内容
        File file = FileUtil.getFile(getContext().getFilesDir().getAbsolutePath(), "cache", "lastInput.dat");
        if (!FileUtil.createParentFile(file)) {
            Log.e(TAG, "fail to create parent file : " + file.getAbsolutePath());
            return;
        }
        SelectedString selectedString = commandEditText.getSelectedString();
        try (DataOutputStream dataOutputStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            dataOutputStream.writeUTF(selectedString.text);
            dataOutputStream.writeInt(selectedString.selectionStart);
            dataOutputStream.writeInt(selectedString.selectionEnd);
        } catch (IOException e) {
            Log.e(TAG, "fail to save file : " + file.getAbsolutePath(), e);
            MonitorUtil.generateCustomLog(e, "IOException");
        }
    }

    private void updateActions() {
        if (isShowActions) {
            fl_action_container.addView(fl_actions);
            btn_action.setBackgroundResource(R.drawable.chevron_down);
        } else {
            fl_action_container.removeView(fl_actions);
            btn_action.setBackgroundResource(R.drawable.chevron_up);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        isGuiLoaded = true;
        String cpackPath = Settings.INSTANCE.getCpackPath();
        if (core.getCore() == null || !Objects.equals(core.getCore().getPath(), cpackPath)) {
            CHelperCore core1 = null;
            try {
                core1 = CHelperCore.fromAssets(getContext().getAssets(), cpackPath);
            } catch (Throwable throwable) {
                Toaster.show("资源包加载失败");
                Log.w(TAG, "fail to load resource pack", throwable);
                MonitorUtil.generateCustomLog(throwable, "LoadResourcePackException");
            }
            core.setCore(core1);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (core == null) {
            return;
        }
        core.close();
    }

}
