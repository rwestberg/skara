/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.skara.bots.mlbridge;

import org.openjdk.skara.forge.PullRequest;
import org.openjdk.skara.network.URIBuilder;
import org.openjdk.skara.vcs.*;
import org.openjdk.skara.vcs.openjdk.Issue;

import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;

class PullRequestInstance {
    private final PullRequest pr;
    private final Repository localRepo;
    private final Hash targetHash;
    private final Hash headHash;
    private final Hash baseHash;
    private final URI issueTracker;
    private final String projectPrefix;

    PullRequestInstance(Path localRepoPath, PullRequest pr, URI issueTracker, String projectPrefix) {
        this.pr = pr;
        this.issueTracker = issueTracker;
        this.projectPrefix = projectPrefix;

        // Materialize the PR's target ref
        try {
            var repository = pr.repository();
            localRepo = Repository.materialize(localRepoPath, repository.url(), pr.targetRef());
            targetHash = localRepo.fetch(repository.url(), pr.targetRef());
            headHash = localRepo.fetch(repository.url(), pr.headHash().hex());
            baseHash = localRepo.mergeBase(targetHash, headHash);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    Repository localRepo() {
        return this.localRepo;
    }

    Hash baseHash() {
        return this.baseHash;
    }

    Hash headHash() {
        return this.headHash;
    }

    String diffUrl() {
        return pr.webUrl() + ".diff";
    }

    String fetchCommand() {
        var repoUrl = pr.repository().webUrl();
        return "git fetch " + repoUrl + " " + pr.sourceRef() + ":pull/" + pr.id();
    }

    String stats(Hash base, Hash head) {
        try {
            var diff = localRepo.diff(base, head);
            var inserted = diff.added();
            var deleted = diff.removed();
            var modified = diff.modified();
            var linesChanged = inserted + deleted + modified;
            var filesChanged = diff.patches().size();
            return String.format("%d line%s in %d file%s changed: %d ins; %d del; %d mod",
                                 linesChanged,
                                 linesChanged == 1 ? "" : "s",
                                 filesChanged,
                                 filesChanged == 1 ? "" : "s",
                                 inserted,
                                 deleted,
                                 modified);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    Optional<String> issueUrl() {
        var issue = Issue.fromString(pr.title());
        return issue.map(value -> URIBuilder.base(issueTracker).appendPath(projectPrefix + "-" + value.id()).build().toString());
    }

    @FunctionalInterface
    interface CommitFormatter {
        String format(Commit commit);
    }

    String formatCommitMessages(Hash first, Hash last, CommitFormatter formatter) {
        try (var commits = localRepo().commits(first.hex() + ".." + last.hex())) {
            return commits.stream()
                          .map(formatter::format)
                          .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    String id() {
        return pr.id();
    }

    PullRequest pr() {
        return pr;
    }
}
