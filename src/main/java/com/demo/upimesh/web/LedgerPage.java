package com.demo.upimesh.web;

/** Values the JSP reads with jsp:useBean. The servlet fills this before the forward. */
public class LedgerPage {

    private int visitCount;
    private String lastOutcome = "";

    public int getVisitCount() {
        return visitCount;
    }

    public void setVisitCount(int visitCount) {
        this.visitCount = visitCount;
    }

    public String getLastOutcome() {
        return lastOutcome;
    }

    public void setLastOutcome(String lastOutcome) {
        this.lastOutcome = lastOutcome == null ? "" : lastOutcome;
    }
}
