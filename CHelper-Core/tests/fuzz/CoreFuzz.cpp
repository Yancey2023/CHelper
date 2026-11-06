#include <cassert>
#include <chelper/CommandContext.h>
#include <chelper/auto_suggestion/AutoSuggestion.h>
#include <chelper/command_structure/CommandStructure.h>
#include <chelper/linter/Linter.h>
#include <chelper/parameter_hint/ParameterHint.h>
#include <chelper/parser/Parser.h>
#include <chelper/serialization/Serialization.h>
#include <chelper/syntax_highlight/SyntaxHighlight.h>

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
    if (size == 0 || size > 1024 * 1024) return 0;
    const auto mode = data[0] % 3;
    const std::string input(reinterpret_cast<const char *>(data + 1), size - 1);
    if (mode != 2) {
        std::shared_ptr<const CHelper::CPack> pack;
        try {
            pack = mode == 0 ? CHelper::serialization::createCPackByJson(input)
                             : CHelper::serialization::createCPackByBinary(input);
        } catch (const std::exception &) { return 0; }
        const CHelper::CommandContext context(pack, u"probe @s");
        (void) context.getStructure();
        (void) context.getSuggestions(8);
        (void) context.getSyntaxResult();
        (void) context.getErrorReasons();
        return 0;
    }
    std::u16string command;
    for (size_t i = 0; i + 1 < input.size(); i += 2) {
        command.push_back(static_cast<char16_t>(static_cast<unsigned char>(input[i]) |
                                                (static_cast<unsigned char>(input[i + 1]) << 8)));
    }
    CHelper::Node::initializeStaticNodes();
    {
        const auto ast = CHelper::Parser::parse(command, CHelper::Node::NodeAny::getNodeAny());
        const auto syntax = CHelper::SyntaxHighlight::getSyntaxResult(ast);
        assert(syntax.tokenTypes.size() == command.size());
        (void) CHelper::AutoSuggestion::getSuggestions(ast, command.size());
        (void) CHelper::CommandStructure::getStructure(ast);
        (void) CHelper::ParameterHint::getParameterHint(ast, command.size());
        for (const auto &error: CHelper::Linter::getErrorReasons(ast)) {
            assert(error->start <= error->end && error->end <= command.size());
            (void) error->getMessage();
        }
    }
    return 0;
}
