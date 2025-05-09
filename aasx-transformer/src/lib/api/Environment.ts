// lib/api/types.ts

/**
 * AAS4J Environment 타입 정의
 * 백엔드에서 JSON으로 내려주는 구조에 맞추어 필요에 따라 필드를 추가/조정하세요.
 */
export interface Environment {
    assetAdministrationShells: any[];    // AAS 리스트
    submodels: any[];                    // Submodel 리스트
    conceptDescriptions: any[];          // ConceptDescription 리스트
    // 필요하다면 더 구체적인 타입을 지정할 수 있습니다:
    // assetAdministrationShells: AssetAdministrationShell[];
    // submodels: Submodel[];
    // conceptDescriptions: ConceptDescription[];
  }
  