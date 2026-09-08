package com.example.travelhelper_server.vo;

import lombok.Data;

import java.util.List;

@Data
public class TravelRecommendVO {

    private Boolean success;
    private String error;
    private String rawResponse;
    private String city;
    private Integer days;
    private Double totalBudget;
    private List<DailyItinerary> dailyItinerary;
    private BudgetBreakdown budgetBreakdown;
    private List<String> tips;
    private List<String> warnings;

    @Data
    public static class DailyItinerary{
        private Integer day;
        private String date;
        private Timeslot morning;
        private Timeslot afternoon;
        private Timeslot evening;
    }

    @Data
    public static class Timeslot{
        private String spot;
        private String duration;
        private String ticket;
        private String transportation;
        private String description;
    }

    @Data
    public static class BudgetBreakdown{
        private Integer accommodation;
        private Integer food;
        private Integer transportation;
        private Integer tickets;
        private Integer other;
    }
}
