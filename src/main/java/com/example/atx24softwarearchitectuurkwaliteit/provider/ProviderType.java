package com.example.atx24softwarearchitectuurkwaliteit.provider;

/**
 * Bekende provider-namen als String-constanten.
 *
 * Dit is geen enum meer: de factory accepteert elke String als provider-naam,
 * zodat een nieuwe provider toegevoegd kan worden door uitsluitend een nieuwe
 * subpackage aan te maken — zonder deze klasse te wijzigen (OCP).
 *
 * Deze constanten blijven beschikbaar voor gebruik in configuratie, tests en
 * de bestaande providers zodat je geen magic strings in code hebt.
 */
public final class ProviderType {

    public static final String SWIFTSEND  = "SWIFTSEND";
    public static final String SECUREPOST = "SECUREPOST";
    public static final String LEGACYLINK = "LEGACYLINK";
    public static final String ASYNCFLOW  = "ASYNCFLOW";

    private ProviderType() {}
}
