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

#include "CHelperQt.h"
#include "ui_chelper.h"
#include <QClipboard>
#include <QDir>
#include <QFile>
#include <QListWidget>
#include <QStringListModel>

CHelperApp::CHelperApp(QWidget *parent)
    : QMainWindow(parent),
      ui(new Ui::CHelperApp) {
    ui->setupUi(this);
    setWindowIcon(QIcon(":/img/logo.webp"));
#ifdef CHelperDebug
    std::filesystem::path resourcePath(RESOURCE_DIR);
    core = CHelper::CHelperCore::createByDirectory(resourcePath / "resources" / "beta" / "vanilla");
#else
    QFile file = QFile(QDir(":/assets").entryInfoList().first().filePath());
    if (file.open(QIODevice::ReadOnly) && file.isReadable()) {
        const QByteArray cpackData = file.readAll();
        core = CHelper::CHelperCore::createByBinary(std::string_view(cpackData.data(), cpackData.size()));
    }
#endif
    if (core == nullptr) [[unlikely]] {
        throw std::runtime_error("fail to load cpack");
    }
    ui->listView->setModel(new QStringListModel(this));
    ui->listView->setMovement(QListView::Static);
    ui->listView->setEditTriggers(QListView::NoEditTriggers);
    ui->listView->setVerticalScrollMode(QListView::ScrollPerPixel);
    ui->listView->setSpacing(2);
    onTextChanged(nullptr);
    ui->lineEdit->setFocus();
    connect(ui->listView, &QListView::clicked, this, &CHelperApp::onSuggestionClick);
    connect(ui->lineEdit, &QLineEdit::textChanged, this, [this] { onSelectionChanged(); });
    connect(ui->lineEdit, &QLineEdit::cursorPositionChanged, this, &CHelperApp::onSelectionChanged);
    connect(ui->copyButton, &QPushButton::clicked, this, &CHelperApp::copy);
}

CHelperApp::~CHelperApp() {
    delete ui;
    CHelper::CHelperCore::deleteContext(context);
    delete core;
}

void CHelperApp::onTextChanged([[maybe_unused]] const QString &string) {
    onSelectionChanged();
}

void CHelperApp::onSelectionChanged() {
    if (core == nullptr) [[unlikely]] {
        return;
    }
    QString string = ui->lineEdit->text();
    if (string != lastText) [[likely]] {
        // 文本内容改变时重新解析命令，生成新的命令上下文
        lastText = string;
        CHelper::CHelperCore::deleteContext(context);
        context = core->createContext(string.toStdU16String());
    }
    size_t cursorPosition = static_cast<size_t>(ui->lineEdit->cursorPosition());
    if (string.isEmpty()) [[unlikely]] {
        ui->structureLabel->setText("欢迎使用CHelper");
        ui->descriptionLabel->setText("作者：Yancey");
        ui->errorReasonLabel->setText(nullptr);
    } else {
        ui->structureLabel->setText(QString::fromStdU16String(context->getStructure()));
        ui->descriptionLabel->setText(QString::fromStdU16String(context->getParamHint(cursorPosition)));
        std::vector<std::shared_ptr<CHelper::ErrorReason>> errorReasons = context->getErrorReasons();
        if (errorReasons.empty()) [[unlikely]] {
            ui->errorReasonLabel->setText(nullptr);
        } else if (errorReasons.size() == 1) [[unlikely]] {
            ui->errorReasonLabel->setText(QString::fromStdU16String(errorReasons[0]->errorReason));
        } else {
            std::u16string result = u"可能的错误原因：";
            for (size_t i = 0; i < errorReasons.size(); ++i) {
                const auto &errorReason = errorReasons[i];
                result.append(fmt::format(u"\n{}. {}", i, errorReason->errorReason));
            }
            ui->errorReasonLabel->setText(QString::fromStdU16String(result));
        }
    }
    std::vector<CHelper::AutoSuggestion::Suggestion> suggestions = context->getSuggestions(cursorPosition);
    QStringList list;
    for (const CHelper::AutoSuggestion::Suggestion &suggestion: suggestions) {
        list.append(QString::fromStdU16String(
                suggestion.content->description.has_value()
                        ? suggestion.content->name + u" - " + suggestion.content->description.value()
                        : suggestion.content->name));
    }
    reinterpret_cast<QStringListModel *>(ui->listView->model())->setStringList(list);
    ui->listView->scrollToTop();
}

void CHelperApp::onSuggestionClick(const QModelIndex &index) {
    if (context == nullptr) [[unlikely]] {
        return;
    }
    std::optional<std::pair<std::u16string, size_t>> result = context->applySuggestion(
            static_cast<size_t>(ui->lineEdit->cursorPosition()), static_cast<size_t>(index.row()));
    if (result.has_value()) [[likely]] {
        ui->lineEdit->setText(QString::fromStdU16String(result.value().first));
        ui->lineEdit->setCursorPosition(static_cast<int>(result.value().second));
        ui->lineEdit->setFocus();
    } else {
        qDebug() << "suggestion index is out of range: " << index.row();
    }
}

void CHelperApp::copy() const {
    QClipboard *clip = QApplication::clipboard();
    clip->setText(ui->lineEdit->text());
}

int main(int argc, char *argv[]) {
    QApplication application(argc, argv);
    CHelperApp app;
    app.show();
    return QApplication::exec();
}
