{{/* Names, labels and small helpers. "root" is the top-level context ($), "c" the component name. */}}

{{- define "bp.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $n := default .Chart.Name .Values.nameOverride -}}
{{- if contains $n .Release.Name -}}{{- .Release.Name | trunc 63 | trimSuffix "-" -}}{{- else -}}{{- printf "%s-%s" .Release.Name $n | trunc 63 | trimSuffix "-" -}}{{- end -}}
{{- end -}}
{{- end -}}

{{/* resource name of a component: <resourcePrefix><c>, or <fullname>-<c> when resourcePrefix is not set */}}
{{- define "bp.res" -}}
{{- if hasKey .root.Values "resourcePrefix" -}}{{- printf "%s%s" .root.Values.resourcePrefix .c -}}{{- else -}}{{- printf "%s-%s" (include "bp.fullname" .root) .c -}}{{- end -}}
{{- end -}}

{{- define "bp.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .root.Chart.Name .root.Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
app.kubernetes.io/name: {{ .c }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/version: {{ .root.Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
app.kubernetes.io/part-of: baracel-portal
{{- end -}}

{{- define "bp.selector" -}}
app.kubernetes.io/name: {{ .c }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
{{- end -}}

{{- define "bp.image" -}}
{{- printf "%s:%s" .repository (toString .tag) -}}
{{- end -}}

{{/* public URLs and the one issuer everybody sees */}}
{{- define "bp.url" -}}{{- printf "%s://%s" .root.Values.global.scheme .host -}}{{- end -}}
{{/* Keycloak's public base URL (with its path when it shares a host with the portal) and the issuer under it */}}
{{- define "bp.authBase" -}}{{- printf "%s://%s%s" .Values.global.scheme .Values.global.hosts.auth (default "" .Values.global.authPath) -}}{{- end -}}
{{- define "bp.issuer" -}}{{- printf "%s/realms/%s" (include "bp.authBase" .) .Values.global.realm -}}{{- end -}}
{{/* how pods reach Keycloak for admin and token calls: the internal service when set, else the public URL */}}
{{- define "bp.internalAuth" -}}{{- default (include "bp.authBase" .) .Values.config.internalAuthUrl -}}{{- end -}}

{{/* pod security context: global defaults overridden by the component's own */}}
{{- define "bp.podSecurityContext" -}}
{{- $g := deepCopy (default (dict) .root.Values.global.podSecurityContext) -}}
{{- toYaml (mustMergeOverwrite $g (deepCopy (default (dict) .own))) -}}
{{- end -}}

{{/* hardened container defaults */}}
{{- define "bp.containerSecurityContext" -}}
allowPrivilegeEscalation: false
capabilities:
  drop: [ALL]
{{- end -}}

{{- define "bp.imagePullSecrets" -}}
{{- with .Values.global.imagePullSecrets }}
imagePullSecrets:
{{- toYaml . | nindent 2 }}
{{- end }}
{{- end -}}

{{/* scheduling constraints shared by the stateless components */}}
{{- define "bp.scheduling" -}}
{{- with .Values.nodeSelector }}
nodeSelector:
{{- toYaml . | nindent 2 }}
{{- end }}
{{- with .Values.tolerations }}
tolerations:
{{- toYaml . | nindent 2 }}
{{- end }}
{{- with .Values.affinity }}
affinity:
{{- toYaml . | nindent 2 }}
{{- end }}
{{- end -}}

{{/* wait until PostgreSQL answers: args: root, host */}}
{{- define "bp.waitForDb" -}}
- name: wait-for-db
  image: {{ include "bp.image" .root.Values.postgres.image }}
  imagePullPolicy: {{ .root.Values.postgres.image.pullPolicy }}
  command: ["sh", "-c", "until pg_isready -h {{ .host }} -U postgres; do echo waiting for the database; sleep 2; done"]
  securityContext:
    {{- include "bp.containerSecurityContext" . | nindent 4 }}
    runAsUser: 999
    runAsNonRoot: true
  resources: { requests: { cpu: 10m, memory: 16Mi }, limits: { memory: 64Mi } }
{{- end -}}

{{/* ServiceAccount of a component */}}
{{- define "bp.serviceAccount" -}}
apiVersion: v1
kind: ServiceAccount
metadata:
  name: {{ include "bp.res" . }}
  labels: {{- include "bp.labels" . | nindent 4 }}
automountServiceAccountToken: false
{{- end -}}

{{/* optional PodDisruptionBudget of a stateless component */}}
{{- define "bp.pdb" -}}
{{- if .root.Values.podDisruptionBudget.enabled }}
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: {{ include "bp.res" . }}
  labels: {{- include "bp.labels" . | nindent 4 }}
spec:
  maxUnavailable: 1
  selector:
    matchLabels: {{- include "bp.selector" . | nindent 6 }}
{{- end }}
{{- end -}}
