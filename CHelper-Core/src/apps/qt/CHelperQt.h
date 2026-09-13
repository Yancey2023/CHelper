/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#pragma once

#include <QMainWindow>
#include <QStyledItemDelegate>
#include <chelper/CHelperCore.h>

QT_BEGIN_NAMESPACE
namespace Ui {
    class CHelperApp;
}
QT_END_NAMESPACE

class CHelperApp : public QMainWindow {
    Q_OBJECT

public:
    explicit CHelperApp(QWidget *parent = nullptr);

    ~CHelperApp() override;

private slots:
    void onTextChanged(const QString &string);

    void onSelectionChanged();

    void onSuggestionClick(const QModelIndex &index);

    void copy() const;

private:
    Ui::CHelperApp *ui;
    CHelper::CHelperCore *core = nullptr;
    // 当前输入框文本对应的命令上下文，文本内容改变时重新创建
    CHelper::CommandContext *context = nullptr;
    // 上次创建命令上下文时的文本内容，用于判断文本是否真的改变了
    QString lastText;
};

int main(int argc, char *argv[]);
