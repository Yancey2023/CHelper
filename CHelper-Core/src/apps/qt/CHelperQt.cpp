//
// Created by Yancey on 2024-05-25.
//

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
        std::istringstream iss(file.readAll().toStdString());
        core = CHelper::CHelperCore::create([&iss] {
            return CHelper::CPack::createByBinary(iss);
        });
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
    delete core;
}

void CHelperApp::onTextChanged([[maybe_unused]] const QString &string) const {
    onSelectionChanged();
}

void CHelperApp::onSelectionChanged() const {
    if (core == nullptr) [[unlikely]] {
        return;
    }
    QString string = ui->lineEdit->text();
    core->onTextChanged(string.toStdU16String(), ui->lineEdit->cursorPosition());
    if (string == nullptr) [[unlikely]] {
        ui->structureLabel->setText("欢迎使用CHelper");
        ui->descriptionLabel->setText("作者：Yancey");
        ui->errorReasonLabel->setText(nullptr);
    } else {
        ui->structureLabel->setText(QString::fromStdU16String(core->getStructure()));
        ui->descriptionLabel->setText(QString::fromStdU16String(core->getParamHint()));
        std::vector<std::shared_ptr<CHelper::ErrorReason>> errorReasons = core->getErrorReasons();
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
    std::vector<CHelper::AutoSuggestion::Suggestion> *suggestions = core->getSuggestions();
    QStringList list;
    for (const CHelper::AutoSuggestion::Suggestion &suggestion: *suggestions) {
        list.append(QString::fromStdU16String(
                suggestion.content->description.has_value()
                        ? suggestion.content->name + u" - " + suggestion.content->description.value()
                        : suggestion.content->name));
    }
    reinterpret_cast<QStringListModel *>(ui->listView->model())->setStringList(list);
    ui->listView->scrollToTop();
}

void CHelperApp::onSuggestionClick(const QModelIndex &index) const {
    if (core == nullptr) [[unlikely]] {
        return;
    }
    std::optional<std::pair<std::u16string, size_t>> result = core->onSuggestionClick(index.row());
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
