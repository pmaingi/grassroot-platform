//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.11 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2015.07.23 at 01:56:30 AM SAST 
//


package za.org.grassroot.webapp.model.ussd.AAT;

import java.net.URI;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlValue;

@XmlAccessorType(XmlAccessType.FIELD)
public class Option {

    @XmlValue
    final String value;

    @XmlAttribute
    final int command;

    @XmlAttribute
    final int order;

    @XmlAttribute
    final URI callback;

    @XmlAttribute
    final Boolean display;

    public Option(String value, int command, int order, URI callback, Boolean display) {
        this.value = value;
        this.command = command;
        this.order = order;
        this.callback = callback;
        this.display = display;
    }
}