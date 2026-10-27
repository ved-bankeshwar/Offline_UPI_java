package com.demo.upimesh.web;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.SimpleTagSupport;

import java.io.IOException;

/** Prints an amount as rupees. The JSP writes &lt;mesh:rupee value="${account.balance}"/&gt;. */
public class RupeeTag extends SimpleTagSupport {

    private Object value;

    public void setValue(Object value) {
        this.value = value;
    }

    @Override
    public void doTag() throws JspException, IOException {
        String text = value == null ? "" : String.valueOf(value);
        getJspContext().getOut().write("Rs " + text);
    }
}
