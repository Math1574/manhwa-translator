# Rapport de test automatique

## nav_report
**RAPPORT ILLISIBLE** : Expecting value: line 1 column 1 (char 0)


## Bloqueur de publicités (émulateur)

(scénario non exécuté ou sans rapport)

## Interface (émulateur)

| vérification | résultat |
|---|---|
| chapitre suivant pendant une traduction de Gemma : le modèle était bien en train de traduire quand le chapitre a changé | oui |
| chapitre suivant pendant une traduction de Gemma : l'application répond sans attendre la fin de la génération (plus long retard du fil principal < 2,5 s) | oui |
| chapitre suivant pendant une traduction de Gemma : l'application reste ouverte (même processus) | oui |
| chapitre suivant pendant une traduction de Gemma : le nouveau chapitre s'ouvre | oui |
| chapitre suivant pendant une traduction de Gemma : aucune fenêtre « ne répond pas » du système | oui |
| chapitre suivant pendant une traduction de Gemma : le nouveau chapitre est traduit ensuite | oui |
| chapitre suivant pendant une traduction de Gemma : l'application est toujours ouverte à la fin | oui |
| chapitre suivant pendant une traduction de Gemma : aucun plantage ni blocage de l'application dans le journal du système | oui |

**Interface : 8/8 vérifications réussies**

# Problèmes détectés
- nav_report: rapport illisible
- bloqueur : pas de rapport