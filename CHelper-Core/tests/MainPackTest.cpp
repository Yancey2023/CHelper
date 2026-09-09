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

#include <chelper/extension/MainPack.h>
#include <gtest/gtest.h>

namespace CHelper::Test {

    namespace {

        std::unique_ptr<Extension::MainPack> openMainPackDir() {
            std::filesystem::path resourceDir(RESOURCE_DIR);
            Extension::MainPackSource source;
            source.kind = Extension::MainPackSource::Kind::Directory;
            source.directory = resourceDir / "main-pack";
            return Extension::MainPack::open(std::move(source));
        }

    }// namespace

    TEST(MainPackTest, OpenAndListSegments) {
        auto pack = openMainPackDir();
        ASSERT_NE(pack, nullptr);
        EXPECT_EQ(pack->packId(), "chelper-main-pack");
        EXPECT_EQ(pack->layout(), "layered");
        const auto &segments = pack->listSegments();
        EXPECT_EQ(segments.size(), 6u);
        bool hasBetaVanilla = false;
        for (const auto &s: segments) {
            if (s.id == "beta/vanilla") {
                hasBetaVanilla = true;
                EXPECT_EQ(s.version, "1.26.0.29");
            }
        }
        EXPECT_TRUE(hasBetaVanilla);
    }

    TEST(MainPackTest, LoadAllSegments) {
        auto pack = openMainPackDir();
        for (const auto &s: pack->listSegments()) {
            const size_t slash = s.id.find('/');
            auto seg = pack->loadSegment(s.id.substr(0, slash), s.id.substr(slash + 1));
            EXPECT_EQ(seg.segmentId, s.id);
            EXPECT_GT(seg.totalBytes(), 0u);
            // 段视图必须含该段 manifest
            bool hasManifest = false;
            for (const auto &f: seg.files) {
                if (f.relPath == "manifest.json") {
                    hasManifest = true;
                }
            }
            EXPECT_TRUE(hasManifest);
        }
    }

    TEST(MainPackTest, MissingSegmentThrows) {
        auto pack = openMainPackDir();
        EXPECT_THROW(pack->loadSegment("beta", "nonexistent"), std::runtime_error);
    }

}// namespace CHelper::Test
