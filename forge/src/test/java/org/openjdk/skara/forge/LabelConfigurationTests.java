/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.skara.forge;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LabelConfigurationTests {
    @Test
    void simple() {
        var config = LabelConfigurationJson.builder()
                                           .addMatchers("1", List.of(Pattern.compile("cpp$")))
                                           .addMatchers("2", List.of(Pattern.compile("hpp$")))
                                           .build();

        assertEquals(Set.of("1", "2"), config.allowed());

        assertEquals(Set.of("1"), config.label(Set.of(Path.of("a.cpp"))));
        assertEquals(Set.of("2"), config.label(Set.of(Path.of("a.hpp"))));
        assertEquals(Set.of("1", "2"), config.label(Set.of(Path.of("a.cpp"), Path.of("a.hpp"))));
    }

    @Test
    void group() {
        var config = LabelConfigurationJson.builder()
                                           .addMatchers("1", List.of(Pattern.compile("cpp$")))
                                           .addMatchers("2", List.of(Pattern.compile("hpp$")))
                                           .addGroup("both", List.of("1", "2"))
                                           .build();

        assertEquals(Set.of("1", "2", "both"), config.allowed());

        assertEquals(Set.of("1"), config.label(Set.of(Path.of("a.cpp"))));
        assertEquals(Set.of("2"), config.label(Set.of(Path.of("a.hpp"))));
        assertEquals(Set.of("both"), config.label(Set.of(Path.of("a.cpp"), Path.of("a.hpp"))));
    }

    @Test
    void groupAndSingle() {
        var config = LabelConfigurationJson.builder()
                                           .addMatchers("1", List.of(Pattern.compile("cpp$")))
                                           .addMatchers("both", List.of(Pattern.compile("hpp$")))
                                           .addGroup("both", List.of("1", "2"))
                                           .build();

        assertEquals(Set.of("1", "both"), config.allowed());

        assertEquals(Set.of("1"), config.label(Set.of(Path.of("a.cpp"))));
        assertEquals(Set.of("both"), config.label(Set.of(Path.of("a.hpp"))));
        assertEquals(Set.of("both"), config.label(Set.of(Path.of("a.cpp"), Path.of("a.hpp"))));

    }
}
