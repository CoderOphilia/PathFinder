package com.pathfinder.mentor.web;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class DemoMentorCatalog {

    private static final List<MentorCatalogItem> MENTORS = List.of(
            new MentorCatalogItem(
                    "priya-k",
                    "Priya K.",
                    "Senior Backend Engineer",
                    "Google",
                    "$80/hr",
                    "Backend engineering mentor for interview prep and career growth.",
                    "Technology",
                    List.of("Java", "Spring", "Behavioral interviews"),
                    42,
                    List.of("Google", "Amazon", "Shopify")
            ),
            new MentorCatalogItem(
                    "alex-m",
                    "Alex M.",
                    "Staff Software Engineer",
                    "Meta",
                    "$95/hr",
                    "System design and architecture mentor for mid-level engineers.",
                    "Technology",
                    List.of("System design", "Scalability", "Backend architecture"),
                    57,
                    List.of("Meta", "Stripe", "Microsoft")
            ),
            new MentorCatalogItem(
                    "natalie-r",
                    "Natalie R.",
                    "Lead Career Coach",
                    "Accenture",
                    "$70/hr",
                    "Career-switch guidance, resume storytelling, and interview confidence.",
                    "Consulting",
                    List.of("Resume reviews", "Career switch", "Communication"),
                    31,
                    List.of("Deloitte", "Accenture", "KPMG")
            ),
            new MentorCatalogItem(
                    "marcus-l",
                    "Marcus L.",
                    "Principal Product Manager",
                    "Uber",
                    "$85/hr",
                    "Product strategy mentor for PM interview loops and case practice.",
                    "Product",
                    List.of("Product sense", "Execution", "Stakeholder management"),
                    38,
                    List.of("Uber", "Airbnb", "LinkedIn")
            ),
            new MentorCatalogItem(
                    "sonia-v",
                    "Sonia V.",
                    "Director of Analytics",
                    "RBC",
                    "$90/hr",
                    "Data and analytics coaching for SQL, metrics, and experimentation.",
                    "Finance",
                    List.of("SQL", "Analytics", "Experimentation"),
                    44,
                    List.of("RBC", "TD", "Capital One")
            )
    );

    public List<MentorCatalogItem> listMentors() {
        return MENTORS;
    }

    public Optional<MentorCatalogItem> findBySlug(String mentorSlug) {
        if (mentorSlug == null || mentorSlug.trim().isEmpty()) {
            return Optional.empty();
        }
        String normalized = mentorSlug.trim().toLowerCase(Locale.ROOT);
        return MENTORS.stream()
                .filter(mentor -> mentor.slug().equalsIgnoreCase(normalized))
                .findFirst();
    }

    public record MentorCatalogItem(
            String slug,
            String name,
            String title,
            String company,
            String rate,
            String tagline,
            String industry,
            List<String> skills,
            int sessionsCompleted,
            List<String> interviewCompanies
    ) {
        public String roleAtCompany() {
            return title + " @ " + company;
        }
    }
}
