package com.pathfinder.mentee.service;

import com.pathfinder.auth.domain.User;
import com.pathfinder.auth.repo.UserRepository;
import com.pathfinder.auth.service.UserService;
import com.pathfinder.mentee.domain.MenteeExperienceLevel;
import com.pathfinder.mentee.domain.MenteeProfile;
import com.pathfinder.mentee.dto.CalendarEvent;
import com.pathfinder.mentee.dto.CalenderDay;
import com.pathfinder.mentee.dto.MentorDirectoryItemView;
import com.pathfinder.mentee.dto.WeekdayOption;
import com.pathfinder.mentee.repo.MenteeRepository;
import com.pathfinder.mentor.domain.MentorProfile;
import com.pathfinder.mentor.repo.MentorProfileRepository;
import com.pathfinder.mentor.service.MentorProfileService;
import com.pathfinder.session.domain.SessionRequest;
import com.pathfinder.session.domain.SessionStatus;
import com.pathfinder.session.repo.SessionRequestRepository;
import com.pathfinder.session.service.SessionService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class MenteeProfileService {
    private final MenteeRepository menteeRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final MentorProfileService mentorProfileService;
    private final SessionRequestRepository sessionRequestRepository;
    private final SessionService sessionService;


    private List<MentorDirectoryItemView> cachedMentors = null;
    private long cacheTime = 0;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;
    private static final List<WeekdayOption> WEEKDAYS = List.of(
            new WeekdayOption("mon", "Monday"),
            new WeekdayOption("tue", "Tuesday"),
            new WeekdayOption("wed", "Wednesday"),
            new WeekdayOption("thu", "Thursday"),
            new WeekdayOption("fri", "Friday"),
            new WeekdayOption("sat", "Saturday"),
            new WeekdayOption("sun", "Sunday")
    );


    // saving Mentee profile
    public MenteeProfile saveMenteeProfile(Long userId,
                                           String targetRole,
                                           String experienceLevel,
                                           String timeZone,
                                           String currentGoals) {
        return saveMenteeProfile(userId, "", "", targetRole, experienceLevel, timeZone, currentGoals);
    }

    public MenteeProfile saveMenteeProfile(Long userId,
                                           String fullName,
                                           String profileImageUrl,
                                           String targetRole,
                                           String experienceLevel,
                                           String timeZone,
                                           String currentGoals) {
        ;
        User user = userRepository.getReferenceById(userId);
        applyUserDetails(user, fullName, profileImageUrl);

        MenteeProfile profile = menteeRepository.findById(userId)
                .orElseGet(() -> {
                    MenteeProfile p = new MenteeProfile();
                    p.setUser(user);
                    return p;
                });

        profile.setTargetRole(normalizeText(targetRole));
        profile.setExperienceLevel(MenteeExperienceLevel.valueOf(normalizeText(experienceLevel)));
        profile.setCurrentGoals(normalizeText(currentGoals));
        profile.setTimezone(normalizeText(timeZone));
        return menteeRepository.save(profile);
    }

    public List<MentorDirectoryItemView> getAllMentors() {
        if (cachedMentors == null || System.currentTimeMillis() - cacheTime > CACHE_TTL_MS) {
            cachedMentors = mentorProfileService.listPublicMentors().stream()
                    .map(profile -> new MentorDirectoryItemView(
                            profile.slug(),
                            profile.name(),
                            profile.profileImageUrl(),
                            profile.rate(),
                            profile.offersFreeSession(),
                            profile.trialSessionLabel(),
                            profile.roleAtCompany(),
                            profile.tagline(),
                            profile.skills(),
                            profile.interviewCompanies(),
                            profile.sessionsCompleted()
                    ))
                    .sorted(Comparator.comparing(MentorDirectoryItemView::name))
                    .toList();
            cacheTime = System.currentTimeMillis();
        }
        return cachedMentors;

    }


    public List<MentorDirectoryItemView> searchFilterMentors(String searchTerm,String interviewCompany) {

        String query = searchTerm == null ? "" : searchTerm.trim().toLowerCase(Locale.ROOT);
        String selectedCompany = safeTrim(interviewCompany);
        if (query.isEmpty() && selectedCompany.isEmpty()) return getAllMentors();


        return getAllMentors().stream()
                .filter(mentor -> matchesQuery(mentor, query))
                .filter(mentor -> selectedCompany.isEmpty() || mentor.interviewCompanies().stream()
                        .anyMatch(company -> company.equalsIgnoreCase(selectedCompany)))
                .toList();

    }

    public  List<String> getCompaniesList(String interviewCompany) {
        List<MentorDirectoryItemView> mentors = getAllMentors();
        return mentors.stream()
                .flatMap(mentor -> mentor.interviewCompanies().stream())
                .distinct()
                .sorted()
                .toList();


    }


    // Getting session

    public Optional<SessionRequest> getNextSessionForMentee(String menteeEmail) {
        // Only show future sessions that are actually approved to happen.
        return  sessionRequestRepository.findByMenteeEmailOrderByCreatedAtDesc(normalizeText(menteeEmail))
                .stream()
                .filter(this::isUpcomingSession)
                .filter(s -> parseSlotTime(s.getSlotTime()).isAfter(LocalDateTime.now()))
                .min(Comparator.comparing(s -> parseSlotTime(s.getSlotTime())));
    }

    public Optional<SessionRequest> getNextSession(String menteeEmail) {
        // Pick the soonest upcoming session for the dashboard card.
        List<SessionRequest> sessionRequests = getMenteeSession(menteeEmail);
        return sessionRequests.stream()
                .filter(this::isUpcomingSession)
                .filter(s -> parseSlotTime(s.getSlotTime()).isAfter(LocalDateTime.now()))
                .min(Comparator.comparing(s -> parseSlotTime(s.getSlotTime())));

    }

    public Optional<SessionRequest> getLatestCompletedSession(String menteeEmail) {
        // Show the most recent completed session as quick progress feedback.
        List<SessionRequest> sessionRequests = getMenteeSession(menteeEmail);
        return sessionRequests.stream()
                .filter(request -> request.getStatus() == SessionStatus.COMPLETED)
                .max(Comparator.comparing(
                        SessionRequest::getCreatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                ));
    }

    public List<SessionRequest> getMenteeSession(String menteeEmail) {
        return menteeEmail.isEmpty()
                ? List.of() : sessionService.getSessionsForMentee(menteeEmail);
    }

    public long getPendingCount(String menteeEmail) {
        List<SessionRequest> sessionRequests = getMenteeSession(menteeEmail);
        return sessionRequests.stream()
                .filter(request -> request.getStatus() == SessionStatus.REQUESTED)
                .count();
    }

    // session calender

//    public List<CalenderDay>  buildCalenderDays(List<SessionRequest> menteeSessions) {
//        List<CalenderDay> calenderDays = new ArrayList<>();
//
//        for (WeekdayOption weekday: WEEKDAYS.stream().filter(day -> !"sun".equals(day.key())).toList()){
//            List<CalendarEvent> events = menteeSessions.stream()
//                    .filter(request -> matchesWeekday(request.getSlotTime(), weekday.label()))
//                    .limit(3)
//                    .map(request -> new CalendarEvent(
//                            shortenSessionLabel(request.getSessionType(), request.getSlotTime())
//                    ))
//                    .toList();
//
//            if(events.isEmpty()) {
//                events = List.of(new CalendarEvent("No sessions planned"));
//            }
//            calenderDays.add(new CalenderDay(shortDayLabel(weekday.label()), events));
//        }
//        return  calenderDays;
//    }
    public List<CalenderDay> buildCalenderDays(List<SessionRequest> menteeSessions) {
        // Build a small week view for the mentee home page.
        List<String> weekdays = List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday");

        return weekdays.stream()
                .map(day -> {
                    List<CalendarEvent> events = menteeSessions.stream()
                            .filter(s -> matchesWeekday(s.getSlotTime(), day))
                            .limit(3)
                            .map(s -> new CalendarEvent(shortenSessionLabel(s.getSessionType(), s.getSlotTime())))
                            .toList();

                    if (events.isEmpty()) {
                        events = List.of(new CalendarEvent("No sessions"));
                    }

                    return new CalenderDay(day.substring(0, 3), events); // "Mon", "Tue" etc
                })
                .toList();
    }








    // helper functions


    private boolean matchesQuery(MentorDirectoryItemView mentor, String query) {
        return containsIgnoreCase(mentor.name(), query)
                || containsIgnoreCase(mentor.roleAtCompany(), query)
                || containsIgnoreCase(mentor.tagline(), query)
//                || containsIgnoreCase(mentor.industry(), query)
                || mentor.skills().stream().anyMatch(skill -> containsIgnoreCase(skill, query))
                || mentor.interviewCompanies().stream().anyMatch(company -> containsIgnoreCase(company, query));
    }
    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    public Optional<User> findUserbyEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public MenteeProfile findProfileByEmail(String accountEmail) {
        User user = findMenteeUserByEmail(accountEmail);
        if (user == null) {
            return null;
        }
        return menteeRepository.findById(user.getId()).orElse(null);
    }
    public Optional<MenteeProfile> findProfileByUser(User user) {
        return menteeRepository.findByUser(user);
    }

    public User findMenteeUserByEmail(String accountEmail) {
        User user = userService.findUserByEmail(accountEmail);
        if (user == null || !isMenteeRole(user.getRole())) {
            return null;
        }
        return user;
    }
    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
    private boolean matchesWeekday(String slotTime, String weekdayLabel) {
        String normalizedSlot = normalizeText(slotTime).toLowerCase(Locale.ROOT);
        return normalizedSlot.contains(weekdayLabel.toLowerCase(Locale.ROOT));
    }

    private boolean isUpcomingSession(SessionRequest sessionRequest) {
        // Requested and finished sessions should not show up as "next".
        return sessionRequest.getStatus() == SessionStatus.APPROVED
                || sessionRequest.getStatus() == SessionStatus.PAID;
    }




    private boolean isMenteeRole(String role) {
        return "mentee".equalsIgnoreCase(role) || "seeker".equalsIgnoreCase(role);
    }
    private LocalDateTime parseSlotTime(String slotTime) {
        try {

            String timePart = slotTime.split("•")[1].trim().split("-")[0].trim();


            String tz = slotTime.replaceAll(".*\\((.*)\\).*", "$1").trim();
            LocalTime time = LocalTime.parse(timePart, DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH));
            ZoneId zone = ZoneId.of(tz);
            return LocalDate.now(zone).atTime(time);
        } catch (Exception e) {
            return LocalDateTime.MAX; // push unparseable slots to the end
        }
    }



    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private void applyUserDetails(User user, String fullName, String profileImageUrl) {
        String normalizedFullName = normalizeText(fullName);
        if (!normalizedFullName.isEmpty()) {
            int firstSpace = normalizedFullName.indexOf(' ');
            if (firstSpace < 0) {
                user.setFirstName(normalizedFullName);
                user.setLastName("");
            } else {
                user.setFirstName(normalizedFullName.substring(0, firstSpace));
                user.setLastName(normalizedFullName.substring(firstSpace + 1).trim());
            }
        }
        user.setProfileImageUrl(userService.normalizeProfileImageUrl(profileImageUrl));
    }

    private String shortenSessionLabel(String sessionType, String slotTime) {
        String type = normalizeText(sessionType);
        String slot = normalizeText(slotTime);
        if (slot.isEmpty()){
            return type;
        }
        return type + " • " + slot;
    }
    private String shortDayLabel(String weekdayLabel) {
        return weekdayLabel.substring(0, 3).toUpperCase(Locale.ROOT);
    }
}
