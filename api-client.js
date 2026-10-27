/* Client FE per l'API FLUPER. Impostare FLUPER_API_BASE_URL prima di questo script. */
(function (global) {
  'use strict';

  const baseUrl = (global.FLUPER_API_BASE_URL || 'http://localhost:8080/api/v1').replace(/\/$/, '');

  async function request(path) {
    const response = await fetch(`${baseUrl}${path}`, {
      headers: { Accept: 'application/json' },
      credentials: 'include'
    });
    if (!response.ok) {
      throw new Error(`Errore API ${response.status} su ${path}`);
    }
    return response.json();
  }

  async function upload(path, formData) {
    const csrf = await request('/csrf');
    const response = await fetch(`${baseUrl}${path}`, {
      method: 'POST',
      headers: { Accept: 'application/json', 'X-CSRF-TOKEN': csrf.token },
      credentials: 'include',
      body: formData
    });
    const body = await response.json();
    if (!response.ok && response.status !== 422) {
      throw new Error(body.message || `Errore API ${response.status} su ${path}`);
    }
    return body;
  }

  global.FluperApi = Object.freeze({
    login: () => global.location.assign(`${baseUrl}/auth/login`),
    health: () => request('/health'),
    me: () => request('/auth/me'),
    catalog: () => request('/catalogo'),
    rilevazioni: () => request('/rilevazioni'),
    enti: () => request('/enti'),
    tipologieEnti: () => request('/enti/tipologie'),
    conferimenti: () => request('/conferimenti'),
    notifiche: () => request('/notifiche'),
    documenti: () => request('/documenti'),
    assistenza: () => request('/assistenza'),
    metricheGoverno: () => request('/governo/metriche'),
    reportistica: () => request('/reportistica'),
    caricaFlusso: (formData) => upload('/conferimenti/upload', formData),
    urlTemplateFlusso: (flusso) => `${baseUrl}/conferimenti/templates/${encodeURIComponent(flusso)}`
  });
})(window);
