package org.openjdk.skara.bots.mlbridge;

import org.openjdk.skara.forge.*;
import org.openjdk.skara.host.HostUser;
import org.openjdk.skara.issuetracker.Comment;
import org.openjdk.skara.vcs.*;

import java.io.IOException;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class ArchiveItem {
    private final String id;
    private final ZonedDateTime created;
    private final ZonedDateTime updated;
    private final HostUser author;
    private final Map<String, String> extraHeaders;
    private final ArchiveItem parent;
    private final Supplier<String> subject;
    private final Supplier<String> header;
    private final Supplier<String> body;
    private final Supplier<String> footer;

    private ArchiveItem(ArchiveItem parent, String id, ZonedDateTime created, ZonedDateTime updated, HostUser author, Map<String, String> extraHeaders, Supplier<String> subject, Supplier<String> header, Supplier<String> body, Supplier<String> footer) {
        this.id = id;
        this.created = created;
        this.updated = updated;
        this.author = author;
        this.extraHeaders = extraHeaders;
        this.parent = parent;
        this.subject = subject;
        this.header = header;
        this.body = body;
        this.footer = footer;
    }

    static ArchiveItem from(PullRequest pr, Repository localRepo, HostUserToEmailAuthor hostUserToEmailAuthor,
                            URI issueTracker, String issuePrefix, WebrevStorage.WebrevGenerator webrevGenerator,
                            WebrevNotification webrevNotification, ZonedDateTime created, ZonedDateTime updated,
                            Hash base, Hash head, String subjectPrefix) {
        return new ArchiveItem(null, "fc", created, updated, pr.author(), Map.of("PR-Head-Hash", head.hex(), "PR-Base-Hash", base.hex()),
                               () -> subjectPrefix + "RFR: " + pr.title(),
                               () -> "",
                               () -> ArchiveMessages.composeConversation(pr, localRepo, base, head),
                               () -> {
                                    var fullWebrev = webrevGenerator.generate(base, head, "00");
                                    webrevNotification.notify(0, fullWebrev, null);
                                    return ArchiveMessages.composeConversationFooter(pr, issueTracker, issuePrefix, localRepo, fullWebrev, base, head);
                               });
    }

    private static Optional<Hash> rebasedLastHead(Repository localRepo, Hash newBase, Hash lastHead) {
        try {
            localRepo.checkout(lastHead, true);
            localRepo.rebase(newBase, "duke", "duke@openjdk.org");
            var rebasedLastHead = localRepo.head();
            return Optional.of(rebasedLastHead);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String hostUserToCommitterName(HostUserToEmailAuthor hostUserToEmailAuthor, HostUser hostUser) {
        var email = hostUserToEmailAuthor.author(hostUser);
        if (email.fullName().isPresent()) {
            return email.fullName().get();
        } else {
            return hostUser.fullName();
        }
    }

    static ArchiveItem from(PullRequest pr, Repository localRepo, HostUserToEmailAuthor hostUserToEmailAuthor,
                            WebrevStorage.WebrevGenerator webrevGenerator, WebrevNotification webrevNotification,
                            ZonedDateTime created, ZonedDateTime updated, Hash lastBase, Hash lastHead, Hash base,
                            Hash head, int index, ArchiveItem parent, String subjectPrefix) {
        return new ArchiveItem(parent,"ha" + head.hex(), created, updated, pr.author(), Map.of("PR-Head-Hash", head.hex(), "PR-Base-Hash", base.hex()),
                               () -> String.format("Re: %s[Rev %02d] RFR: %s", subjectPrefix, index, pr.title()),
                               () -> "",
                               () -> {
                                   if (lastBase.equals(base)) {
                                       return ArchiveMessages.composeIncrementalRevision(localRepo, hostUserToCommitterName(hostUserToEmailAuthor, pr.author()), head, lastHead);
                                   } else {
                                       var rebasedLastHead = rebasedLastHead(localRepo, base, lastHead);
                                       if (rebasedLastHead.isPresent()) {
                                           return ArchiveMessages.composeRebasedIncrementalRevision(localRepo, hostUserToCommitterName(hostUserToEmailAuthor, pr.author()), head, rebasedLastHead.get());
                                       } else {
                                           return ArchiveMessages.composeFullRevision(localRepo, hostUserToCommitterName(hostUserToEmailAuthor, pr.author()), base, head);
                                       }
                                   }
                               },
                               () -> {
                                   var fullWebrev = webrevGenerator.generate(base, head, String.format("%02d", index));
                                   if (lastBase.equals(base)) {
                                       var incrementalWebrev = webrevGenerator.generate(lastHead, head, String.format("%02d-%02d", index - 1, index));
                                       webrevNotification.notify(index, fullWebrev, incrementalWebrev);
                                       return ArchiveMessages.composeIncrementalFooter(pr, localRepo, fullWebrev, incrementalWebrev, head, lastHead);
                                   } else {
                                       var rebasedLastHead = rebasedLastHead(localRepo, base, lastHead);
                                       if (rebasedLastHead.isPresent()) {
                                           var incrementalWebrev = webrevGenerator.generate(rebasedLastHead.get(), head, String.format("%02d-%02d", index - 1, index));
                                           webrevNotification.notify(index, fullWebrev, incrementalWebrev);
                                           return ArchiveMessages.composeIncrementalFooter(pr, localRepo, fullWebrev, incrementalWebrev, head, lastHead);
                                       } else {
                                           webrevNotification.notify(index, fullWebrev, null);
                                           return ArchiveMessages.composeRebasedFooter(pr, localRepo, fullWebrev, base, head);
                                       }
                                   }
                               });
    }

    static ArchiveItem from(PullRequest pr, Comment comment, HostUserToEmailAuthor hostUserToEmailAuthor, ArchiveItem parent) {
        return new ArchiveItem(parent, "pc" + comment.id(), comment.createdAt(), comment.updatedAt(), comment.author(), Map.of(),
                               () -> ArchiveMessages.composeReplySubject(parent.subject()),
                               () -> ArchiveMessages.composeReplyHeader(parent.createdAt(), hostUserToEmailAuthor.author(parent.author)),
                               () -> ArchiveMessages.composeComment(comment),
                               () -> ArchiveMessages.composeReplyFooter(pr));
    }

    static ArchiveItem from(PullRequest pr, Review review, HostUserToEmailAuthor hostUserToEmailAuthor, HostUserToUserName hostUserToUserName, HostUserToRole hostUserToRole, ArchiveItem parent) {
        return new ArchiveItem(parent, "rv" + review.id(), review.createdAt(), review.createdAt(), review.reviewer(), Map.of(),
                               () -> ArchiveMessages.composeReplySubject(parent.subject()),
                               () -> ArchiveMessages.composeReplyHeader(parent.createdAt(), hostUserToEmailAuthor.author(parent.author())),
                               () -> ArchiveMessages.composeReview(pr, review, hostUserToUserName, hostUserToRole),
                               () -> ArchiveMessages.composeReviewFooter(pr, review, hostUserToUserName, hostUserToRole));
    }

    static ArchiveItem from(PullRequest pr, ReviewComment reviewComment, HostUserToEmailAuthor hostUserToEmailAuthor, ArchiveItem parent) {
        return new ArchiveItem(parent, "rc" + reviewComment.id(), reviewComment.createdAt(), reviewComment.updatedAt(), reviewComment.author(), Map.of(),
                               () -> ArchiveMessages.composeReplySubject(parent.subject()),
                               () -> ArchiveMessages.composeReplyHeader(parent.createdAt(), hostUserToEmailAuthor.author(parent.author())),
                               () -> ArchiveMessages.composeReviewComment(pr, reviewComment) ,
                               () -> ArchiveMessages.composeReplyFooter(pr));
    }

    private static Pattern mentionPattern = Pattern.compile("^@([\\w-]+).*");

    private static Optional<ArchiveItem> findLastMention(String commentText, List<ArchiveItem> eligibleParents) {
        var mentionMatcher = mentionPattern.matcher(commentText);
        if (mentionMatcher.matches()) {
            var username = mentionMatcher.group(1);
            for (int i = eligibleParents.size() - 1; i != 0; --i) {
                if (eligibleParents.get(i).author.userName().equals(username)) {
                    return Optional.of(eligibleParents.get(i));
                }
            }
        }
        return Optional.empty();
    }

    static ArchiveItem findParent(List<ArchiveItem> generated, Comment comment) {
        ArchiveItem lastCommentOrReview = generated.get(0);
        var eligible = new ArrayList<ArchiveItem>();
        eligible.add(lastCommentOrReview);
        for (var item : generated) {
            if (item.id().startsWith("pc") || item.id().startsWith("rv")) {
                if (item.createdAt().isBefore(comment.createdAt()) && item.createdAt().isAfter(lastCommentOrReview.createdAt())) {
                    lastCommentOrReview = item;
                    eligible.add(lastCommentOrReview);
                }
            }
        }

        var lastMention = findLastMention(comment.body(), eligible);
        if (lastMention.isPresent()) {
            return lastMention.get();
        }

        return lastCommentOrReview;
    }

    static ArchiveItem findRevisionItem(List<ArchiveItem> generated, Hash hash) {
        // Parent is revision update mail with the hash
        ArchiveItem lastRevisionItem = generated.get(0);
        for (var item : generated) {
            if (item.id().startsWith("ha")) {
                lastRevisionItem = item;
            }
            if (item.id().equals("ha" + hash.hex())) {
                return item;
            }
        }
        return lastRevisionItem;
    }

    static ArchiveItem findReviewCommentItem(List<ArchiveItem> generated, ReviewComment reviewComment) {
        for (var item : generated) {
            if (item.id().equals("rc" + reviewComment.id())) {
                return item;
            }
        }
        throw new RuntimeException("Failed to find review comment");
    }

    static ArchiveItem findParent(List<ArchiveItem> generated, Review review) {
        return findRevisionItem(generated, review.hash());
    }

    static ArchiveItem findParent(List<ArchiveItem> generated, List<ReviewComment> reviewComments, ReviewComment reviewComment) {
        // Parent is previous in thread or the revision update mail with the hash

        var threadId = reviewComment.threadId();
        var reviewThread = reviewComments.stream()
                                         .filter(comment -> comment.threadId().equals(threadId))
                                         .collect(Collectors.toList());
        ReviewComment previousComment = null;
        var eligible = new ArrayList<ArchiveItem>();
        for (var threadComment : reviewThread) {
            if (threadComment.equals(reviewComment)) {
                break;
            }
            previousComment = threadComment;
            eligible.add(findReviewCommentItem(generated, previousComment));
        }

        if (previousComment == null) {
            return findRevisionItem(generated, reviewComment.hash());
        } else {
            var mentionedParent = findLastMention(reviewComment.body(), eligible);
            if (mentionedParent.isPresent()) {
                return mentionedParent.get();
            } else {
                return eligible.get(eligible.size() - 1);
            }
        }
    }

    String id() {
        return id;
    }

    ZonedDateTime createdAt() {
        return created;
    }

    ZonedDateTime updatedAt() {
        return updated;
    }

    HostUser author() {
        return author;
    }

    Map<String, String> extraHeaders() {
        return extraHeaders;
    }

    Optional<ArchiveItem> parent() {
        return Optional.ofNullable(parent);
    }

    String subject() {
        return subject.get();
    }

    String header() {
        return header.get();
    }

    String body() {
        return body.get();
    }

    String footer() {
        return footer.get();
    }
}
