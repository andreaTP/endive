;; Calls its first function through its table, from the elem segment and after a table.set.
(module
  (type $i32 (func (result i32)))
  (table 2 funcref)

  (func $uncompiled (result i32) (i32.const 7))

  (elem (i32.const 0) $uncompiled)

  (func (export "callElem") (result i32)
    (call_indirect (type $i32) (i32.const 0)))

  (func (export "setAndCall") (result i32)
    (table.set (i32.const 1) (ref.func $uncompiled))
    (call_indirect (type $i32) (i32.const 1)))
)
