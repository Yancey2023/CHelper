//
// Created by Yancey on 2024-05-25.
//

#ifndef CHELPER_CHELPERQT_H
#define CHELPER_CHELPERQT_H

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
    void onTextChanged(const QString &string) const;

    void onSelectionChanged() const;

    void onSuggestionClick(const QModelIndex &index) const;

    void copy() const;

private:
    Ui::CHelperApp *ui;
    CHelper::CHelperCore *core = nullptr;
};

int main(int argc, char *argv[]);

#endif//CHELPER_CHELPERQT_H
