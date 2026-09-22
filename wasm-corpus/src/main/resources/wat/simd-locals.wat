(module
  (func (export "local_roundtrip") (result i32) (local v128)
    (local.set 0 (v128.const i32x4 7 8 9 10))
    (i32x4.extract_lane 3 (local.get 0)))

  (func (export "local_roundtrip_lane0") (result i32) (local v128)
    (local.set 0 (v128.const i32x4 7 8 9 10))
    (i32x4.extract_lane 0 (local.get 0)))

  (func (export "local_tee") (result i32) (local v128)
    (i32x4.extract_lane 3 (local.tee 0 (v128.const i32x4 7 8 9 10))))

  (func (export "local_tee_get") (result i32) (local v128)
    (drop (local.tee 0 (v128.const i32x4 7 8 9 10)))
    (i32x4.extract_lane 0 (local.get 0)))
)
