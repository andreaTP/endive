(module
  ;; `noexn` is the bottom of the exception hierarchy, a subtype of `exn` only.

  (global $g (ref null noexn) (ref.null noexn))

  (func (export "null-to-exnref") (result exnref)
    (ref.null noexn)
  )

  (func (export "is-null") (result i32)
    (ref.null noexn)
    (ref.is_null)
  )

  ;; a noexn-typed parameter widens to exnref
  (func $widen (param $x (ref null noexn)) (result exnref)
    (local.get $x)
  )

  (func (export "roundtrip") (result i32)
    (call $widen (ref.null noexn))
    (ref.is_null)
  )

  (func (export "from-global") (result i32)
    (global.get $g)
    (ref.is_null)
  )

  ;; a table of noexn
  (table $t 2 (ref null noexn))

  (func (export "from-table") (result i32)
    (table.get $t (i32.const 0))
    (ref.is_null)
  )
)
